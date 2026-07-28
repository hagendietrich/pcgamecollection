package com.example.digitalcollectionmanager.data.repository

import com.example.digitalcollectionmanager.data.api.GogClient
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.api.SteamClient
import com.example.digitalcollectionmanager.data.api.models.*
import com.example.digitalcollectionmanager.data.dao.GameDao
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

data class UnmatchedGame(
    val storeTitle: String,
    val storeId: String,
    val platform: String,
    val playtimeMinutes: Int,
    val candidates: List<IgdbGame>
)

data class SyncResult(
    val importedCount: Int,
    val unmatchedGames: List<UnmatchedGame> = emptyList()
)

class GameRepository(
    private val gameDao: GameDao,
    private val igdbClient: IgdbClient,
    private val steamClient: SteamClient,
    private val gogClient: GogClient,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Searches for games on IGDB.
     */
    suspend fun searchGames(query: String): List<IgdbGame> {
        val clientId = settingsRepository.clientId.firstOrNull() ?: return emptyList()
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return emptyList()

        val authSuccess = igdbClient.authenticate(clientId, clientSecret)
        if (!authSuccess) return emptyList()

        return igdbClient.searchGames(clientId, query)
    }

    /**
     * Syncs games from a public Steam profile using Web API.
     */
    suspend fun syncSteamGames(
        steamIdInput: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.steamApiKey.firstOrNull() ?: return@withContext SyncResult(-1)

        onProgress(0.02f, "Resolving Steam ID...")
        val steamId = steamClient.resolveVanityUrl(apiKey, steamIdInput) ?: return@withContext SyncResult(0)

        onProgress(0.05f, "Fetching games from Steam API...")
        val steamGames = steamClient.fetchOwnedGames(apiKey, steamId)
        if (steamGames.isEmpty()) return@withContext SyncResult(0)

        // Local Lookup Optimization: Get existing Steam IDs from database
        val localGames = gameDao.getAllGames().first()
        val localSteamToIgdb = localGames.mapNotNull { game ->
            val appId = game.sourceIds["STEAM"]
            val igdbId = game.igdbId
            if (appId != null && igdbId != null) appId to igdbId else null
        }.toMap()

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0)
        igdbClient.authenticate(clientId, clientSecret)

        val igdbIdToSteamId = mutableMapOf<Long, String>()
        val unknownSteamGames = mutableListOf<SteamClient.SteamGame>()
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        // Separate known from unknown
        steamGames.forEach { steamGame ->
            val appId = steamGame.appid.toString()
            val knownIgdbId = localSteamToIgdb[appId]
            if (knownIgdbId != null) {
                igdbIdToSteamId[knownIgdbId] = appId
            } else {
                unknownSteamGames.add(steamGame)
            }
        }

        // Step 1: Resolve ONLY unknown Steam IDs to IGDB IDs
        if (unknownSteamGames.isNotEmpty()) {
            val batches = unknownSteamGames.map { it.appid.toString() }.chunked(50)
            batches.forEachIndexed { index, batch ->
                val progress = 0.1f + (0.1f * (index.toFloat() / batches.size))
                onProgress(progress, "Resolving new Steam IDs on IGDB...")
                
                val externalGames = igdbClient.resolveExternalGames(clientId, IgdbExternalCategory.STEAM, batch)
                externalGames.filter { it.game != null }.forEach { 
                    igdbIdToSteamId[it.game!!] = it.uid 
                }
            }
        }

        // Step 2: Fallback - Search by Title for unmatched games
        val matchedAppIds = igdbIdToSteamId.values.toSet()
        val stillUnknownSteamGames = unknownSteamGames.filter { it.appid.toString() !in matchedAppIds }
        
        stillUnknownSteamGames.forEachIndexed { index, steamGame ->
            val progress = 0.2f + (0.4f * (index.toFloat() / stillUnknownSteamGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching: ${steamGame.name}...")
            
            val match = findBestIgdbMatch(clientId, steamGame.name)
            if (match != null) {
                igdbIdToSteamId[match.id] = steamGame.appid.toString()
            } else {
                // Collect candidates for manual matching
                val candidates = igdbClient.searchGames(clientId, cleanTitle(steamGame.name))
                unmatchedGames.add(UnmatchedGame(
                    storeTitle = steamGame.name,
                    storeId = steamGame.appid.toString(),
                    platform = "Steam",
                    playtimeMinutes = steamGame.playtime_forever,
                    candidates = candidates.take(10)
                ))
                println("Sync Warning: Could not find Steam game on IGDB: '${steamGame.name}'")
            }
        }
        
        // Step 3: Fetch metadata ONLY for IDs we don't have locally
        val localIgdbIds = localGames.mapNotNull { it.igdbId }.toSet()
        val newIgdbIds = igdbIdToSteamId.keys.filter { it !in localIgdbIds }
        val igdbGamesMetadata = mutableMapOf<Long, IgdbGame>()
        
        if (newIgdbIds.isNotEmpty()) {
            val metadataBatches = newIgdbIds.chunked(50)
            metadataBatches.forEachIndexed { index, batch ->
                val progress = 0.6f + (0.2f * (index.toFloat() / metadataBatches.size))
                onProgress(progress, "Fetching metadata for new games...")
                
                igdbClient.getGamesByIds(clientId, batch).forEach {
                    igdbGamesMetadata[it.id] = it
                }
            }
        }

        // Step 4: Save / Merge
        var importedCount = 0
        val totalToImport = igdbIdToSteamId.size
        igdbIdToSteamId.entries.forEachIndexed { index, (igdbId, steamAppId) ->
            val progress = 0.8f + (0.2f * (index.toFloat() / totalToImport))
            if (index % 10 == 0) onProgress(progress, "Updating library...")

            val steamGame = steamGames.find { it.appid.toString() == steamAppId } ?: return@forEachIndexed
            val existingGame = gameDao.getGameByIgdbId(igdbId)
            
            if (existingGame != null) {
                // Merging known game (Fast)
                val updatedPlatforms = existingGame.platforms.toMutableList()
                if (updatedPlatforms.contains("IGDB")) updatedPlatforms.remove("IGDB")
                if (!updatedPlatforms.contains("Steam")) updatedPlatforms.add("Steam")
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                updatedPlaytimes["Steam"] = steamGame.playtime_forever
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["STEAM"] = steamAppId
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    sourceIds = updatedSourceIds,
                    playtimeMinutes = updatedPlaytimes.values.sum(),
                    releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else existingGame.releaseDate
                ))
            } else {
                // New game (Requires metadata we fetched)
                val igdbGame = igdbGamesMetadata[igdbId] ?: return@forEachIndexed
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf("Steam"),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = formatTimestamp(igdbGame.firstReleaseDate),
                    igdbId = igdbId,
                    sourceIds = mapOf("STEAM" to steamAppId),
                    playtimes = mapOf("Steam" to steamGame.playtime_forever),
                    playtimeMinutes = steamGame.playtime_forever,
                    genres = igdbGame.genres?.map { it.name } ?: emptyList(),
                    summary = igdbGame.summary,
                    screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
                    igdbUrl = igdbGame.url
                )
                gameDao.insertGame(game)
                importedCount++
            }
        }
        onProgress(1.0f, "Import complete!")
        SyncResult(importedCount, unmatchedGames)
    }

    /**
     * Syncs games from a public GOG profile.
     */
    suspend fun syncGogGames(
        username: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Fetching games from GOG...")
        val gogGames = gogClient.fetchPublicGames(username)
        if (gogGames.isEmpty()) return@withContext SyncResult(0)

        // Local Lookup Optimization
        val localGames = gameDao.getAllGames().first()
        val localGogToIgdb = localGames.mapNotNull { game ->
            val gogId = game.sourceIds["GOG"]
            val igdbId = game.igdbId
            if (gogId != null && igdbId != null) gogId to igdbId else null
        }.toMap()

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0)
        igdbClient.authenticate(clientId, clientSecret)

        val igdbIdToGogId = mutableMapOf<Long, String>()
        val unknownGogGames = mutableListOf<GogClient.GogGame>()
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        gogGames.forEach { gogGame ->
            val knownIgdbId = localGogToIgdb[gogGame.gogId]
            if (knownIgdbId != null) {
                igdbIdToGogId[knownIgdbId] = gogGame.gogId
            } else {
                unknownGogGames.add(gogGame)
            }
        }

        // Step 1: Resolve ONLY unknown GOG IDs to IGDB IDs
        if (unknownGogGames.isNotEmpty()) {
            val batches = unknownGogGames.map { it.gogId }.chunked(50)
            batches.forEachIndexed { index, batch ->
                val progress = 0.1f + (0.1f * (index.toFloat() / batches.size))
                onProgress(progress, "Resolving new GOG IDs on IGDB...")
                
                val externalGames = igdbClient.resolveExternalGames(clientId, IgdbExternalCategory.GOG, batch)
                externalGames.filter { it.game != null }.forEach { 
                    igdbIdToGogId[it.game!!] = it.uid 
                }
            }
        }

        // Step 2: Fallback for games not matched by ID - Search by Title
        val matchedGogIds = igdbIdToGogId.values.toSet()
        val stillUnknownGogGames = unknownGogGames.filter { it.gogId !in matchedGogIds }
        
        stillUnknownGogGames.forEachIndexed { index, gogGame ->
            val progress = 0.2f + (0.4f * (index.toFloat() / stillUnknownGogGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching: ${gogGame.title}...")
            
            val match = findBestIgdbMatch(clientId, gogGame.title)
            if (match != null) {
                igdbIdToGogId[match.id] = gogGame.gogId
            } else {
                // Collect candidates for manual matching
                val candidates = igdbClient.searchGames(clientId, cleanTitle(gogGame.title))
                unmatchedGames.add(UnmatchedGame(
                    storeTitle = gogGame.title,
                    storeId = gogGame.gogId,
                    platform = "GOG",
                    playtimeMinutes = gogGame.playtimeMinutes,
                    candidates = candidates.take(10)
                ))
                println("Sync Warning: Could not find GOG game on IGDB: '${gogGame.title}'")
            }
        }

        // Step 3: Fetch metadata ONLY for IDs we don't have locally
        val localIgdbIds = localGames.mapNotNull { it.igdbId }.toSet()
        val newIgdbIds = igdbIdToGogId.keys.filter { it !in localIgdbIds }
        val igdbGamesMetadata = mutableMapOf<Long, IgdbGame>()
        
        if (newIgdbIds.isNotEmpty()) {
            val metadataBatches = newIgdbIds.chunked(50)
            metadataBatches.forEachIndexed { index, batch ->
                val progress = 0.6f + (0.2f * (index.toFloat() / metadataBatches.size))
                onProgress(progress, "Fetching metadata for new games...")
                
                igdbClient.getGamesByIds(clientId, batch).forEach {
                    igdbGamesMetadata[it.id] = it
                }
            }
        }

        // Step 4: Save / Merge
        var importedCount = 0
        val totalToImport = igdbIdToGogId.size
        igdbIdToGogId.entries.forEachIndexed { index, (igdbId, gogSourceId) ->
            val progress = 0.8f + (0.2f * (index.toFloat() / totalToImport))
            if (index % 10 == 0) onProgress(progress, "Updating library...")

            val gogGame = gogGames.find { it.gogId == gogSourceId } ?: return@forEachIndexed
            val existingGame = gameDao.getGameByIgdbId(igdbId)
            
            if (existingGame != null) {
                val updatedPlatforms = existingGame.platforms.toMutableList()
                if (updatedPlatforms.contains("IGDB")) updatedPlatforms.remove("IGDB")
                if (!updatedPlatforms.contains("GOG")) updatedPlatforms.add("GOG")
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                updatedPlaytimes["GOG"] = gogGame.playtimeMinutes
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["GOG"] = gogSourceId
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    sourceIds = updatedSourceIds,
                    playtimeMinutes = updatedPlaytimes.values.sum(),
                    releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else existingGame.releaseDate
                ))
            } else {
                val igdbGame = igdbGamesMetadata[igdbId] ?: return@forEachIndexed
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf("GOG"),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = formatTimestamp(igdbGame.firstReleaseDate),
                    igdbId = igdbId,
                    sourceIds = mapOf("GOG" to gogSourceId),
                    playtimes = mapOf("GOG" to gogGame.playtimeMinutes),
                    playtimeMinutes = gogGame.playtimeMinutes,
                    genres = igdbGame.genres?.map { it.name } ?: emptyList(),
                    summary = igdbGame.summary,
                    screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
                    igdbUrl = igdbGame.url
                )
                gameDao.insertGame(game)
                importedCount++
            }
        }
        onProgress(1.0f, "Import complete!")
        SyncResult(importedCount, unmatchedGames)
    }

    /**
     * Syncs games from a Playnite JSON export.
     */
    suspend fun syncPlayniteGames(
        playniteGames: List<PlayniteGame>,
        statusMapping: Map<String, CompletionStatus>,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0)
        igdbClient.authenticate(clientId, clientSecret)

        val localGames = gameDao.getAllGames().first()
        val igdbIdToPlayniteGame = mutableMapOf<Long, PlayniteGame>()
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        playniteGames.forEachIndexed { index, pGame ->
            val progress = 0.1f + (0.7f * (index.toFloat() / playniteGames.size))
            if (index % 10 == 0) onProgress(progress, "Matching: ${pGame.name}...")

            val match = findBestIgdbMatch(clientId, pGame.name)
            if (match != null) {
                igdbIdToPlayniteGame[match.id] = pGame
            } else {
                val candidates = igdbClient.searchGames(clientId, cleanTitle(pGame.name))
                unmatchedGames.add(UnmatchedGame(
                    storeTitle = pGame.name,
                    storeId = pGame.gameId ?: "N/A",
                    platform = pGame.source?.name ?: "Playnite",
                    playtimeMinutes = (pGame.playtime / 60).toInt(),
                    candidates = candidates.take(10)
                ))
            }
        }

        // Fetch metadata for new games
        val localIgdbIds = localGames.mapNotNull { it.igdbId }.toSet()
        val newIgdbIds = igdbIdToPlayniteGame.keys.filter { it !in localIgdbIds }
        val igdbGamesMetadata = mutableMapOf<Long, IgdbGame>()

        if (newIgdbIds.isNotEmpty()) {
            val metadataBatches = newIgdbIds.chunked(50)
            metadataBatches.forEachIndexed { index, batch ->
                val progress = 0.8f + (0.1f * (index.toFloat() / metadataBatches.size))
                onProgress(progress, "Fetching metadata...")
                igdbClient.getGamesByIds(clientId, batch).forEach {
                    igdbGamesMetadata[it.id] = it
                }
            }
        }

        var importedCount = 0
        igdbIdToPlayniteGame.entries.forEachIndexed { index, (igdbId, pGame) ->
            val progress = 0.9f + (0.1f * (index.toFloat() / igdbIdToPlayniteGame.size))
            if (index % 10 == 0) onProgress(progress, "Updating library...")

            val existingGame = gameDao.getGameByIgdbId(igdbId)
            val playtimeMin = (pGame.playtime / 60).toInt()
            val sourcePlatform = pGame.source?.name ?: "Playnite"
            val status = pGame.completionStatus?.name?.let { statusMapping[it] } ?: CompletionStatus.BACKLOG

            if (existingGame != null) {
                val updatedPlatforms = existingGame.platforms.toMutableList()
                if (!updatedPlatforms.contains(sourcePlatform)) updatedPlatforms.add(sourcePlatform)
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                updatedPlaytimes[sourcePlatform] = playtimeMin
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    playtimeMinutes = updatedPlaytimes.values.sum(),
                    completionStatus = status,
                    releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else normalizeDate(pGame.releaseDate?.releaseDate)
                ))
            } else {
                val igdbGame = igdbGamesMetadata[igdbId] ?: return@forEachIndexed
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf(sourcePlatform),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = normalizeDate(pGame.releaseDate?.releaseDate) ?: formatTimestamp(igdbGame.firstReleaseDate),
                    igdbId = igdbId,
                    playtimes = mapOf(sourcePlatform to playtimeMin),
                    playtimeMinutes = playtimeMin,
                    genres = igdbGame.genres?.map { it.name } ?: emptyList(),
                    completionStatus = status,
                    summary = igdbGame.summary,
                    screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
                    igdbUrl = igdbGame.url
                )
                gameDao.insertGame(game)
                importedCount++
            }
        }

        onProgress(1.0f, "Playnite import complete!")
        SyncResult(importedCount, unmatchedGames)
    }

    /**
     * Manually links an unmatched store game to a selected IGDB game.
     */
    suspend fun linkGameManually(unmatchedGame: UnmatchedGame, selectedIgdbGame: IgdbGame) = withContext(Dispatchers.IO) {
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext
        
        // Fetch full metadata for the selected IGDB game (since candidates only have basic info)
        val fullIgdbGames = igdbClient.getGamesByIds(clientId, listOf(selectedIgdbGame.id))
        val igdbGame = fullIgdbGames.firstOrNull() ?: selectedIgdbGame

        val existingGame = gameDao.getGameByIgdbId(igdbGame.id)
        val sourceKey = if (unmatchedGame.platform == "Steam") "STEAM" else "GOG"

        if (existingGame != null) {
            val updatedPlatforms = existingGame.platforms.toMutableList()
            if (updatedPlatforms.contains("IGDB")) updatedPlatforms.remove("IGDB")
            if (!updatedPlatforms.contains(unmatchedGame.platform)) updatedPlatforms.add(unmatchedGame.platform)
            
            val updatedPlaytimes = existingGame.playtimes.toMutableMap()
            updatedPlaytimes[unmatchedGame.platform] = unmatchedGame.playtimeMinutes
            
            val updatedSourceIds = existingGame.sourceIds.toMutableMap()
            updatedSourceIds[sourceKey] = unmatchedGame.storeId
            
            gameDao.updateGame(existingGame.copy(
                platforms = updatedPlatforms,
                playtimes = updatedPlaytimes,
                sourceIds = updatedSourceIds,
                playtimeMinutes = updatedPlaytimes.values.sum()
            ))
        } else {
            val game = Game(
                title = igdbGame.name,
                platforms = listOf(unmatchedGame.platform),
                coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                releaseDate = normalizeDate(formatTimestamp(igdbGame.firstReleaseDate)),
                igdbId = igdbGame.id,
                sourceIds = mapOf(sourceKey to unmatchedGame.storeId),
                playtimes = mapOf(unmatchedGame.platform to unmatchedGame.playtimeMinutes),
                playtimeMinutes = unmatchedGame.playtimeMinutes,
                genres = igdbGame.genres?.map { it.name } ?: emptyList(),
                summary = igdbGame.summary,
                screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
                igdbUrl = igdbGame.url
            )
            gameDao.insertGame(game)
        }
    }

    private suspend fun findBestIgdbMatch(clientId: String, rawTitle: String): IgdbGame? {
        // Stage 1: Exact Match (Raw)
        val stage1 = igdbClient.searchGames(clientId, rawTitle)
        stage1.find { it.name.equals(rawTitle, ignoreCase = true) }?.let { return it }

        // Stage 2: Cleaned Match (Smart suffixes + symbol removal)
        val cleanedTitle = cleanTitle(rawTitle)
        val stage2 = if (cleanedTitle != rawTitle) {
            igdbClient.searchGames(clientId, cleanedTitle)
        } else stage1
        stage2.find { cleanTitle(it.name).equals(cleanedTitle, ignoreCase = true) }?.let { return it }

        // Stage 3: Roman Numeral Swap (4 -> IV, etc.)
        val romanTitle = cleanedTitle.replace(" 4", " IV").replace(" 3", " III").replace(" 2", " II")
        if (romanTitle != cleanedTitle) {
            val stage3 = igdbClient.searchGames(clientId, romanTitle)
            stage3.find { cleanTitle(it.name).equals(cleanTitle(romanTitle), ignoreCase = true) }?.let { return it }
        }

        // Stage 4: Broad Core Title Match (Before first colon/hyphen/plus)
        val coreTitle = rawTitle.split(":", "-", "+", "(")[0].trim()
        if (coreTitle.length > 3 && coreTitle != rawTitle && coreTitle != cleanedTitle) {
            val stage4 = igdbClient.searchGames(clientId, coreTitle)
            // Pick exact match in core search first
            stage4.find { it.name.equals(coreTitle, ignoreCase = true) }?.let { return it }
            // Otherwise, if any result's cleaned name contains our core title
            stage4.find { cleanTitle(it.name).contains(cleanTitle(coreTitle), ignoreCase = true) }?.let { return it }
        }

        // Stage 5: Normalization Match (Last Resort: Remove ALL non-alphanumeric)
        val normalizedRaw = rawTitle.filter { it.isLetterOrDigit() }.lowercase()
        stage2.find { it.name.filter { c -> c.isLetterOrDigit() }.lowercase() == normalizedRaw }?.let { return it }

        // Stage 6: Fuzzy fallback (Accept first result if it contains the cleaned title)
        stage2.firstOrNull()?.let { 
            if (cleanTitle(it.name).contains(cleanedTitle, ignoreCase = true)) return it 
        }

        return null
    }

    fun cleanTitle(title: String): String {
        return title
            .replace("®", "")
            .replace("™", "")
            .replace("©", "")
            .replace(Regex("\\(.*?\\)"), "") // Remove ANY parentheses content
            .replace("GOTY Edition", "")
            .replace("GOTY", "")
            .replace("Gold Edition", "")
            .replace("Gold", "")
            .replace("Definitive Edition", "")
            .replace("Enhanced Edition", "")
            .replace("Complete Season", "")
            .replace("Complete Edition", "")
            .replace("Classic", "")
            .replace("2019 REBALANCE", "")
            .replace(":", " ")
            .replace("-", " ")
            .replace("+", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    suspend fun addGame(game: Game) {
        gameDao.insertGame(game)
    }

    suspend fun updateGame(game: Game) {
        gameDao.updateGame(game)
    }

    /**
     * Updates an existing game with new metadata from IGDB, but preserves source IDs and playtime.
     */
    suspend fun reMatchGame(gameId: Int, newIgdbGame: IgdbGame) = withContext(Dispatchers.IO) {
        val existingGame = gameDao.getGameById(gameId) ?: return@withContext
        
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext
        val fullIgdbGames = igdbClient.getGamesByIds(clientId, listOf(newIgdbGame.id))
        val igdbGame = fullIgdbGames.firstOrNull() ?: newIgdbGame
        
        val updatedGame = existingGame.copy(
            title = igdbGame.name,
            coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
            releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else normalizeDate(formatTimestamp(igdbGame.firstReleaseDate)),
            igdbId = igdbGame.id,
            genres = igdbGame.genres?.map { it.name } ?: emptyList(),
            summary = igdbGame.summary,
            screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
            igdbUrl = igdbGame.url
            // Keep: id, platforms, isOwned, sourceIds, playtimes, playtimeMinutes, labels, completionStatus
        )
        
        gameDao.updateGame(updatedGame)
    }

    suspend fun deleteGame(game: Game) {
        gameDao.deleteGame(game)
    }

    fun getAllGames(): Flow<List<Game>> {
        return gameDao.getAllGames()
    }

    suspend fun getGameByTitle(title: String): Game? {
        return gameDao.getGameByTitle(title)
    }

    suspend fun getGameByIgdbId(igdbId: Long): Game? {
        return gameDao.getGameByIgdbId(igdbId)
    }

    fun getFullCoverUrl(thumbUrl: String?): String? {
        return thumbUrl?.let { igdbClient.getFullCoverUrl(it) }
    }

    fun getFullScreenshotUrl(thumbUrl: String): String {
        return "https:" + thumbUrl.replace("t_thumb", "t_screenshot_huge")
    }

    fun formatTimestamp(timestamp: Long?): String? {
        if (timestamp == null) return null
        
        // If it's just a year (e.g. from IGDB or previous logic)
        if (timestamp > 1900 && timestamp < 2100) {
            return "$timestamp-01-01"
        }
        
        return try {
            val date = Date(timestamp * 1000L)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            format.format(date)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Normalizes a date string to YYYY-MM-DD format.
     */
    fun normalizeDate(dateStr: String?): String? {
        if (dateStr == null || dateStr.isBlank()) return null
        
        val trimmed = dateStr.trim()
        
        // Handle YYYY
        if (trimmed.length == 4 && trimmed.all { it.isDigit() }) {
            return "$trimmed-01-01"
        }
        
        // Handle YYYY-M-D and variants
        val parts = trimmed.split("-", "/", ".")
        if (parts.size == 3) {
            val y = parts[0].padStart(4, '2').takeLast(4) // Assume YYYY
            val m = parts[1].padStart(2, '0')
            val d = parts[2].padStart(2, '0')
            return "$y-$m-$d"
        }
        
        return trimmed
    }

    /**
     * Generates a JSON string representing the entire game collection.
     */
    suspend fun generateJsonContent(): String = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGames().first()
        val json = Json { prettyPrint = true }
        json.encodeToString(games)
    }

    /**
     * Imports games from a JSON string.
     */
    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val importedGames: List<Game> = json.decodeFromString(jsonString)
        val localGames = gameDao.getAllGames().first()
        
        // Deduplicate: Map imported games to existing local IDs where possible
        val mergedGames = importedGames.map { imported ->
            val existing = localGames.find { local ->
                (imported.igdbId != null && local.igdbId == imported.igdbId) ||
                (local.title.equals(imported.title, ignoreCase = true))
            }
            
            val normalizedImported = imported.copy(
                releaseDate = normalizeDate(imported.releaseDate)
            )

            if (existing != null) {
                // Keep the database ID of the existing record to trigger an UPDATE instead of an INSERT
                // If the existing game has a manual date, and the imported one doesn't, we might want to keep the manual one.
                // But usually a JSON import/restore is meant to be a full state sync.
                // We'll trust the JSON state (which includes the manual flag).
                normalizedImported.copy(id = existing.id)
            } else {
                // No match found, insert as a new game
                normalizedImported.copy(id = 0)
            }
        }
        
        gameDao.insertGames(mergedGames)
    }

    /**
     * Wipes the entire game collection.
     */
    suspend fun clearLibrary() = withContext(Dispatchers.IO) {
        gameDao.deleteAllGames()
    }

    /**
     * Fetches missing metadata (summary, screenshots, url) for a game if not already present.
     */
    suspend fun refreshGameMetadata(gameId: Int) = withContext(Dispatchers.IO) {
        val existing = gameDao.getGameById(gameId) ?: return@withContext
        val igdbId = existing.igdbId ?: return@withContext

        // Only fetch if missing essential new metadata
        if (!existing.summary.isNullOrBlank() && existing.screenshotUrls.isNotEmpty()) return@withContext

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext
        igdbClient.authenticate(clientId, clientSecret)

        val igdbGames = igdbClient.getGamesByIds(clientId, listOf(igdbId))
        val igdbGame = igdbGames.firstOrNull() ?: return@withContext

        gameDao.updateGame(existing.copy(
            summary = igdbGame.summary,
            screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
            igdbUrl = igdbGame.url
        ))
    }
}
