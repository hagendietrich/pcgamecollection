package com.example.digitalcollectionmanager.data.repository

import com.example.digitalcollectionmanager.data.api.EpicClient
import com.example.digitalcollectionmanager.data.api.GogClient
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.api.SteamClient
import com.example.digitalcollectionmanager.data.api.models.*
import com.example.digitalcollectionmanager.data.dao.GameDao
import com.example.digitalcollectionmanager.data.dao.IgnoredGameDao
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.IgnoredGame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
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
    private val ignoredGameDao: IgnoredGameDao,
    private val igdbClient: IgdbClient,
    private val steamClient: SteamClient,
    private val gogClient: GogClient,
    private val epicClient: EpicClient,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Syncs games from Epic Games Store.
     */
    suspend fun syncEpicGames(
        code: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Exchanging Epic code...")
        val tokenResponse = epicClient.exchangeCodeForToken(code) ?: return@withContext SyncResult(0)
        
        onProgress(0.2f, "Fetching Epic library...")
        val epicRecords = epicClient.fetchLibraryItems(tokenResponse.accessToken)
        println("Epic Sync: Fetched ${epicRecords.size} library items")
        epicRecords.forEach { record ->
            println("Epic Sync: Found library item - Title: '${record.sandboxName}', Namespace: '${record.namespace}', CatalogItemId: '${record.catalogItemId}'")
        }
        if (epicRecords.isEmpty()) return@withContext SyncResult(0)

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("Epic").toSet()
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0)
        igdbClient.authenticate(clientId, clientSecret)

        val localGames = gameDao.getAllGames().first()
        var importedCount = 0
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        epicRecords.forEachIndexed { index, record ->
            // Skip if ignored
            if (ignoredIds.contains(record.catalogItemId)) {
                println("Epic Sync Filter: Skipping ignored game - CatalogItemId: '${record.catalogItemId}'")
                return@forEachIndexed
            }

            // Skip if recordType is not APPLICATION (e.g., DLCs, add-ons)
            if (record.recordType != "APPLICATION") {
                println("Epic Sync Filter: Skipping non-application record - Type: '${record.recordType}', CatalogItemId: '${record.catalogItemId}'")
                return@forEachIndexed
            }
            
            println("Epic Sync: Processing library item - SandboxName: '${record.sandboxName}', Namespace: '${record.namespace}'")

            val title = record.sandboxName ?: ""
            val catalogItemId = record.catalogItemId
            
            // Skip if sandboxName is blank or looks like an internal ID
            if (title.isBlank() || title.matches(Regex("^[a-f0-9]{32}$"))) {
                println("Epic Sync Filter: Skipping invalid sandboxName '$title' - CatalogItemId: '${record.catalogItemId}'")
                return@forEachIndexed
            }
            
            // Check if the game already exists with the same title AND Epic platform
            val existingGameWithEpic = localGames.find {
                it.title.equals(title, ignoreCase = true) && it.platforms.contains("Epic")
            }
            
            if (existingGameWithEpic != null) {
                println("Epic Sync: Game already exists with Epic platform - Title: '$title'")
                return@forEachIndexed
            }
            
            // Check for exact title match (case-insensitive)
            val existingGameByExactTitle = localGames.find { it.title.equals(title, ignoreCase = true) }
            
            if (existingGameByExactTitle != null) {
                println("Epic Sync: Game exists under another platform - Title: '$title'")
                
                // Add Epic to platforms and update sourceIds with catalogItemId
                val updatedPlatforms = existingGameByExactTitle.platforms.toMutableList().apply { add("Epic") }
                val updatedSourceIds = existingGameByExactTitle.sourceIds.toMutableMap().apply { put("EPIC", catalogItemId) }
                
                gameDao.updateGame(existingGameByExactTitle.copy(
                    platforms = updatedPlatforms,
                    sourceIds = updatedSourceIds
                ))
                importedCount++
                return@forEachIndexed
            }
            
            // Fallback: Fuzzy title matching (e.g., "Ghostrunner II" vs. "Ghostrunner 2")
            val existingGameByFuzzyTitle = localGames.find { fuzzyTitleMatch(it.title, title) }
            
            if (existingGameByFuzzyTitle != null) {
                println("Epic Sync: Fuzzy title match found - Title: '$title' (Existing: '${existingGameByFuzzyTitle.title}')")
                return@forEachIndexed
            }

            val progress = 0.2f + (0.8f * (index.toFloat() / epicRecords.size))
            if (index % 5 == 0) onProgress(progress, "Matching: ${record.sandboxName}...")

            val existingGame = localGames.find { it.sourceIds["EPIC"] == record.catalogItemId }
            if (existingGame != null) {
                // Update existing
                val updatedPlatforms = existingGame.platforms.toMutableList()
                if (!updatedPlatforms.contains("Epic")) updatedPlatforms.add("Epic")
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["EPIC"] = record.catalogItemId
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    sourceIds = updatedSourceIds
                ))
                importedCount++
            } else {
                // Search IGDB
                val match = findBestIgdbMatch(clientId, record.sandboxName ?: "")
                if (match != null) {
                    val fullMatch = igdbClient.getGamesByIds(clientId, listOf(match.id)).firstOrNull() ?: match
                    
                val game = Game(
                    title = fullMatch.name,
                    platforms = listOf("Epic"),
                    coverImageUrl = getFullCoverUrl(fullMatch.cover?.url),
                    releaseDate = formatTimestamp(fullMatch.firstReleaseDate),
                    igdbId = fullMatch.id,
                    sourceIds = mapOf("EPIC" to record.catalogItemId),
                        genres = fullMatch.genres?.map { it.name } ?: emptyList(),
                        summary = fullMatch.summary,
                        screenshotUrls = fullMatch.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
                        igdbUrl = fullMatch.url,
                        userRating = fullMatch.rating,
                        criticRating = fullMatch.aggregatedRating,
                        developers = fullMatch.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
                        publishers = fullMatch.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
                        themes = fullMatch.themes?.map { it.name } ?: emptyList(),
                        keywords = fullMatch.keywords?.map { it.name } ?: emptyList(),
                        gameModes = mapIgdbGameModes(fullMatch.gameModes),
                        storeUrls = extractStoreUrls(fullMatch, mapOf("EPIC" to record.catalogItemId))
                    )
                    gameDao.insertGame(game)
                    importedCount++
                } else {
                    val candidates = igdbClient.searchGames(clientId, cleanTitle(title))
                    unmatchedGames.add(UnmatchedGame(
                    storeTitle = title,
                    storeId = record.catalogItemId,
                        platform = "Epic",
                        playtimeMinutes = 0,
                        candidates = candidates.take(10)
                    ))
                }
            }
        }

        onProgress(1.0f, "Epic sync complete!")
        SyncResult(importedCount, unmatchedGames)
    }

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

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("Steam").toSet()
        val filteredSteamGames = steamGames.filter { it.appid.toString() !in ignoredIds }
        if (filteredSteamGames.isEmpty()) return@withContext SyncResult(0)

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
        filteredSteamGames.forEach { steamGame ->
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

            val steamGame = filteredSteamGames.find { it.appid.toString() == steamAppId } ?: return@forEachIndexed
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

                // Try to find IGDB metadata if it was missing to get the store URL
                val igdbGame = igdbGamesMetadata[igdbId]
                val updatedStoreUrls = existingGame.storeUrls.toMutableMap()
                if (igdbGame != null) {
                    updatedStoreUrls.putAll(extractStoreUrls(igdbGame, updatedSourceIds))
                } else if (updatedSourceIds.containsKey("STEAM")) {
                    updatedStoreUrls["Steam"] = "https://store.steampowered.com/app/${updatedSourceIds["STEAM"]}"
                }
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    sourceIds = updatedSourceIds,
                    playtimeMinutes = updatedPlaytimes.values.sum(),
                    storeUrls = updatedStoreUrls,
                    genres = if (existingGame.isGenreManual) existingGame.genres else (igdbGame?.genres?.map { it.name } ?: existingGame.genres),
                    gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame?.gameModes),
                    coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame?.cover?.url) ?: existingGame.coverImageUrl)
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
                    igdbUrl = igdbGame.url,
                    userRating = igdbGame.rating,
                    criticRating = igdbGame.aggregatedRating,
                    developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
                    publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
                    themes = igdbGame.themes?.map { it.name } ?: emptyList(),
                    keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
                    gameModes = mapIgdbGameModes(igdbGame.gameModes),
                    storeUrls = extractStoreUrls(igdbGame, mapOf("STEAM" to steamAppId)).toMutableMap().apply {
                        if (!containsKey("Steam")) put("Steam", "https://store.steampowered.com/app/$steamAppId")
                    }
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

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("GOG").toSet()
        val filteredGogGames = gogGames.filter { it.gogId !in ignoredIds }
        if (filteredGogGames.isEmpty()) return@withContext SyncResult(0)

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

        filteredGogGames.forEach { gogGame ->
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

            val gogGame = filteredGogGames.find { it.gogId == gogSourceId } ?: return@forEachIndexed
            val existingGame = gameDao.getGameByIgdbId(igdbId)
            
            if (existingGame != null) {
                val updatedPlatforms = existingGame.platforms.toMutableList()
                if (updatedPlatforms.contains("IGDB")) updatedPlatforms.remove("IGDB")
                if (!updatedPlatforms.contains("GOG")) updatedPlatforms.add("GOG")
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                updatedPlaytimes["GOG"] = gogGame.playtimeMinutes
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["GOG"] = gogSourceId

                val igdbGame = igdbGamesMetadata[igdbId]
                val updatedStoreUrls = existingGame.storeUrls.toMutableMap()
                if (igdbGame != null) {
                    updatedStoreUrls.putAll(extractStoreUrls(igdbGame, updatedSourceIds))
                }
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    sourceIds = updatedSourceIds,
                    playtimeMinutes = updatedPlaytimes.values.sum(),
                    storeUrls = updatedStoreUrls,
                    genres = if (existingGame.isGenreManual) existingGame.genres else (igdbGame?.genres?.map { it.name } ?: existingGame.genres),
                    gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame?.gameModes),
                    coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame?.cover?.url) ?: existingGame.coverImageUrl)
                ))
            } else {
                val igdbGame = igdbGamesMetadata[igdbId] ?: return@forEachIndexed
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf("GOG"),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = normalizeDate(formatTimestamp(igdbGame.firstReleaseDate)),
                    igdbId = igdbId,
                    sourceIds = mapOf("GOG" to gogSourceId),
                    playtimes = mapOf("GOG" to gogGame.playtimeMinutes),
                    playtimeMinutes = gogGame.playtimeMinutes,
                    genres = igdbGame.genres?.map { it.name } ?: emptyList(),
                    summary = igdbGame.summary,
                    screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
                    igdbUrl = igdbGame.url,
                    userRating = igdbGame.rating,
                    criticRating = igdbGame.aggregatedRating,
                    developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
                    publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
                    themes = igdbGame.themes?.map { it.name } ?: emptyList(),
                    keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
                    gameModes = mapIgdbGameModes(igdbGame.gameModes),
                    storeUrls = extractStoreUrls(igdbGame, mapOf("GOG" to gogSourceId))
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
                
                val igdbGame = igdbGamesMetadata[igdbId]
                val updatedStoreUrls = existingGame.storeUrls.toMutableMap()
                if (igdbGame != null) {
                    updatedStoreUrls.putAll(extractStoreUrls(igdbGame, existingGame.sourceIds))
                }

                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    playtimeMinutes = updatedPlaytimes.values.sum(),
                    completionStatus = status,
                    releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else normalizeDate(pGame.releaseDate?.releaseDate),
                    storeUrls = updatedStoreUrls,
                    genres = if (existingGame.isGenreManual) existingGame.genres else (igdbGame?.genres?.map { it.name } ?: existingGame.genres),
                    gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame?.gameModes),
                    coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame?.cover?.url) ?: existingGame.coverImageUrl)
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
                    igdbUrl = igdbGame.url,
                    userRating = igdbGame.rating,
                    criticRating = igdbGame.aggregatedRating,
                    developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
                    publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
                    themes = igdbGame.themes?.map { it.name } ?: emptyList(),
                    keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
                    gameModes = mapIgdbGameModes(igdbGame.gameModes),
                    storeUrls = extractStoreUrls(igdbGame, emptyMap())
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
            
            val updatedStoreUrls = existingGame.storeUrls.toMutableMap()
            updatedStoreUrls.putAll(extractStoreUrls(igdbGame, updatedSourceIds))

            gameDao.updateGame(existingGame.copy(
                platforms = updatedPlatforms,
                playtimes = updatedPlaytimes,
                sourceIds = updatedSourceIds,
                playtimeMinutes = updatedPlaytimes.values.sum(),
                storeUrls = updatedStoreUrls,
                genres = if (existingGame.isGenreManual) existingGame.genres else (igdbGame.genres?.map { it.name } ?: existingGame.genres),
                gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame.gameModes),
                coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame.cover?.url) ?: existingGame.coverImageUrl)
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
                igdbUrl = igdbGame.url,
                userRating = igdbGame.rating,
                criticRating = igdbGame.aggregatedRating,
                developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
                publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
                themes = igdbGame.themes?.map { it.name } ?: emptyList(),
                keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
                gameModes = mapIgdbGameModes(igdbGame.gameModes),
                storeUrls = extractStoreUrls(igdbGame, mapOf(sourceKey to unmatchedGame.storeId))
            )
            gameDao.insertGame(game)
        }
    }

    private suspend fun findBestIgdbMatch(clientId: String, rawTitle: String): IgdbGame? {
        // Preference for main games, remakes, remasters, expanded, ports
        val gameCategories = listOf(0, 8, 9, 10, 11)

        fun List<IgdbGame>.pickBest(): IgdbGame? {
            return this.filter { it.category in gameCategories }
                .sortedBy { gameCategories.indexOf(it.category) }
                .firstOrNull() ?: this.firstOrNull()
        }

        // Stage 1: Exact Match (Raw)
        val stage1 = igdbClient.searchGames(clientId, rawTitle)
        stage1.find { it.name.equals(rawTitle, ignoreCase = true) && it.category in gameCategories }?.let { return it }
        stage1.find { it.name.equals(rawTitle, ignoreCase = true) }?.let { return it }

        // Stage 2: Cleaned Match (Smart suffixes + symbol removal)
        val cleanedTitle = cleanTitle(rawTitle)
        val stage2 = if (cleanedTitle != rawTitle) {
            igdbClient.searchGames(clientId, cleanedTitle)
        } else stage1
        
        stage2.filter { cleanTitle(it.name).equals(cleanedTitle, ignoreCase = true) }.pickBest()?.let { return it }

        // Stage 3: Roman Numeral Swap (4 -> IV, etc.)
        val romanTitle = cleanedTitle.replace(" 4", " IV").replace(" 3", " III").replace(" 2", " II")
        if (romanTitle != cleanedTitle) {
            val stage3 = igdbClient.searchGames(clientId, romanTitle)
            stage3.filter { cleanTitle(it.name).equals(cleanTitle(romanTitle), ignoreCase = true) }.pickBest()?.let { return it }
        }

        // Stage 4: Broad Core Title Match (Before first colon/hyphen/plus)
        val coreTitle = rawTitle.split(":", "-", "+", "(")[0].trim()
        if (coreTitle.length > 3 && coreTitle != rawTitle && coreTitle != cleanedTitle) {
            val stage4 = igdbClient.searchGames(clientId, coreTitle)
            // Pick exact match in core search first
            stage4.find { it.name.equals(coreTitle, ignoreCase = true) && it.category in gameCategories }?.let { return it }
            // Otherwise, if any result's cleaned name contains our core title
            stage4.filter { cleanTitle(it.name).contains(cleanTitle(coreTitle), ignoreCase = true) }.pickBest()?.let { return it }
        }

        // Stage 5: Normalization Match (Last Resort: Remove ALL non-alphanumeric)
        val normalizedRaw = rawTitle.filter { it.isLetterOrDigit() }.lowercase()
        stage2.filter { it.name.filter { c -> c.isLetterOrDigit() }.lowercase() == normalizedRaw }.pickBest()?.let { return it }

        // Stage 6: Fuzzy fallback (Accept first result if it contains the cleaned title)
        stage2.filter { cleanTitle(it.name).contains(cleanedTitle, ignoreCase = true) }.pickBest()?.let { return it }

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
            coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else getFullCoverUrl(igdbGame.cover?.url),
            releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else normalizeDate(formatTimestamp(igdbGame.firstReleaseDate)),
            igdbId = igdbGame.id,
            genres = if (existingGame.isGenreManual) existingGame.genres else (igdbGame.genres?.map { it.name } ?: emptyList()),
            summary = igdbGame.summary,
            screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
            igdbUrl = igdbGame.url,
            userRating = igdbGame.rating,
            criticRating = igdbGame.aggregatedRating,
            developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
            publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
            themes = igdbGame.themes?.map { it.name } ?: emptyList(),
            keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
            gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame.gameModes),
            storeUrls = extractStoreUrls(igdbGame, existingGame.sourceIds)
            // Keep: id, platforms, isOwned, sourceIds, playtimes, playtimeMinutes, labels, completionStatus
        )
        
        gameDao.updateGame(updatedGame)
    }

    suspend fun deleteGame(game: Game) {
        gameDao.deleteGame(game)
    }

    suspend fun deleteGameAndIgnore(game: Game) = withContext(Dispatchers.IO) {
        gameDao.deleteGame(game)
        
        // Add to ignore list for each applicable platform
        if (game.platforms.contains("Steam") && game.sourceIds.containsKey("STEAM")) {
            ignoredGameDao.insertIgnoredGame(IgnoredGame(title = game.title, platform = "Steam", catalogItemId = game.sourceIds["STEAM"]!!))
        }
        if (game.platforms.contains("GOG") && game.sourceIds.containsKey("GOG")) {
            ignoredGameDao.insertIgnoredGame(IgnoredGame(title = game.title, platform = "GOG", catalogItemId = game.sourceIds["GOG"]!!))
        }
        if (game.platforms.contains("Epic") && game.sourceIds.containsKey("EPIC")) {
            ignoredGameDao.insertIgnoredGame(IgnoredGame(title = game.title, platform = "Epic", catalogItemId = game.sourceIds["EPIC"]!!))
        }
    }

    fun getIgnoredGames(): Flow<List<IgnoredGame>> {
        return ignoredGameDao.getAllIgnoredGames()
    }

    suspend fun ignoreGame(title: String, platform: String, catalogItemId: String) {
        ignoredGameDao.insertIgnoredGame(IgnoredGame(title = title, platform = platform, catalogItemId = catalogItemId))
    }

    suspend fun removeIgnoredGame(id: Int) {
        ignoredGameDao.deleteIgnoredGame(id)
    }

    fun getAllGames(): Flow<List<Game>> {
        return gameDao.getAllGames()
    }

    fun getAllGamesSortedByDateAdded(): Flow<List<Game>> {
        return gameDao.getAllGamesSortedByDateAdded()
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

        // Refresh if missing summary OR screenshots OR companies OR store links OR game modes
        val needsRefresh = existing.summary.isNullOrBlank() || 
                           existing.screenshotUrls.isEmpty() || 
                           existing.developers.isEmpty() ||
                           existing.storeUrls.isEmpty() ||
                           existing.gameModes.isEmpty()

        if (!needsRefresh) return@withContext

        println("Enriching metadata for: ${existing.title} (IGDB ID: $igdbId)")

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext
        
        val authSuccess = igdbClient.authenticate(clientId, clientSecret)
        if (!authSuccess) {
            println("Enrichment Failed: IGDB Auth error")
            return@withContext
        }

        val igdbGames = igdbClient.getGamesByIds(clientId, listOf(igdbId))
        val igdbGame = igdbGames.firstOrNull() ?: run {
            println("Enrichment Failed: No game found on IGDB for ID $igdbId")
            return@withContext
        }

        val updatedGame = existing.copy(
            summary = igdbGame.summary,
            screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
            igdbUrl = igdbGame.url,
            userRating = igdbGame.rating,
            criticRating = igdbGame.aggregatedRating,
            developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
            publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
            themes = igdbGame.themes?.map { it.name } ?: emptyList(),
            keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
            gameModes = if (existing.isGameModeManual) existing.gameModes else mapIgdbGameModes(igdbGame.gameModes),
            genres = if (existing.isGenreManual) existing.genres else (igdbGame.genres?.map { it.name } ?: existing.genres),
            storeUrls = extractStoreUrls(igdbGame, existing.sourceIds)
        )
        
        gameDao.updateGame(updatedGame)
        println("Enrichment Complete for: ${existing.title}")
    }

    fun mapIgdbGameModes(igdbModes: List<IgdbGameMode>?): List<String> {
        if (igdbModes == null) return emptyList()
        val result = mutableSetOf<String>()
        igdbModes.forEach { mode ->
            val name = mode.name.lowercase()
            when {
                name.contains("single") -> result.add("Singleplayer")
                name.contains("multiplayer") || name.contains("mmo") -> result.add("Multiplayer")
                name.contains("co-op") || name.contains("cooperative") -> result.add("Co-op")
            }
        }
        return sortGameModes(result.toList())
    }

    /**
     * Sorts game modes in the canonical order: Singleplayer, Multiplayer, Co-op.
     */
    private fun fuzzyTitleMatch(existingTitle: String, newTitle: String): Boolean {
        // Replace Roman numerals with numeric equivalents
        val romanToNumeric = mapOf(
            "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
            "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9"
        )
        
        // Normalize titles: lowercase, replace Roman numerals, and remove non-alphanumeric characters
        val normalizedExisting = romanToNumeric.entries.fold(existingTitle.lowercase()) { acc, (roman, numeric) ->
            acc.replace(roman, numeric)
        }.replace(Regex("[^a-z0-9]"), "")
        
        val normalizedNew = romanToNumeric.entries.fold(newTitle.lowercase()) { acc, (roman, numeric) ->
            acc.replace(roman, numeric)
        }.replace(Regex("[^a-z0-9]"), "")
        
        return normalizedExisting == normalizedNew
    }

    fun sortGameModes(modes: List<String>): List<String> {
        val order = listOf("Singleplayer", "Multiplayer", "Co-op")
        return modes.sortedBy { mode ->
            val index = order.indexOf(mode)
            if (index != -1) index else 99
        }
    }

    /**
     * Extracts store URLs from IGDB metadata.
     */
    fun extractStoreUrls(igdbGame: IgdbGame, sourceIds: Map<String, String>): Map<String, String> {
        val urls = mutableMapOf<String, String>()
        
        // Steam (Category 1)
        val steamId = sourceIds["STEAM"] ?: igdbGame.externalGames?.find { it.category == IgdbExternalCategory.STEAM }?.uid
        if (steamId != null) {
            urls["Steam"] = "https://store.steampowered.com/app/$steamId"
        }

        // GOG (Category 5)
        // If we have a URL from IGDB, use it.
        val gogUrl = igdbGame.externalGames?.find { it.category == IgdbExternalCategory.GOG }?.url
        if (gogUrl != null) {
            urls["GOG"] = gogUrl
        }

        // Epic Games (Category 26)
        val epicUrl = igdbGame.externalGames?.find { it.category == IgdbExternalCategory.EPIC_GAMES }?.url
        if (epicUrl != null) {
            urls["Epic"] = epicUrl
        }

        return urls
    }

    /**
     * Fetches a list of alternative portrait covers for a game.
     * Searches IGDB by title and also includes Steam vertical library art if available.
     */
    suspend fun fetchAlternativeCovers(gameId: Int): List<String> = withContext(Dispatchers.IO) {
        val game = gameDao.getGameById(gameId) ?: return@withContext emptyList()
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext emptyList()
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext emptyList()

        igdbClient.authenticate(clientId, clientSecret)
        
        val results = igdbClient.searchGames(clientId, game.title)
        val covers = results.mapNotNull { getFullCoverUrl(it.cover?.url) }.toMutableSet()
        
        // Add Steam vertical library art if we have a Steam ID
        val steamId = game.sourceIds["STEAM"] ?: game.sourceIds["EA"] // Sometimes EA games have Steam IDs
        if (steamId != null) {
            covers.add("https://steamcdn-a.akamaihd.net/steam/apps/$steamId/library_600x900.jpg")
        }
        
        // Also check if IGDB knows about a Steam ID
        results.forEach { igdbGame ->
            val externalSteamId = igdbGame.externalGames?.find { it.category == IgdbExternalCategory.STEAM }?.uid
            if (externalSteamId != null) {
                covers.add("https://steamcdn-a.akamaihd.net/steam/apps/$externalSteamId/library_600x900.jpg")
            }
        }
        
        covers.toList()
    }
}
