package com.github.hagendietrich.pcgamecollection.data.repository

import com.github.hagendietrich.pcgamecollection.data.api.BattleNetClient
import com.github.hagendietrich.pcgamecollection.data.api.EpicClient
import android.util.Log
import com.github.hagendietrich.pcgamecollection.data.api.GogClient
import com.github.hagendietrich.pcgamecollection.data.api.IgdbClient
import com.github.hagendietrich.pcgamecollection.data.api.SteamClient
import com.github.hagendietrich.pcgamecollection.data.api.UbisoftClient
import com.github.hagendietrich.pcgamecollection.data.api.models.*
import com.github.hagendietrich.pcgamecollection.data.dao.GameDao
import com.github.hagendietrich.pcgamecollection.data.dao.IgnoredGameDao
import com.github.hagendietrich.pcgamecollection.data.dao.WishlistGameDao
import com.github.hagendietrich.pcgamecollection.data.model.BackupData
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.model.IgnoredGame
import com.github.hagendietrich.pcgamecollection.data.model.PlatformPrice
import com.github.hagendietrich.pcgamecollection.data.model.WishlistGame
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
    val alreadyPresentCount: Int = 0,
    val ignoredCount: Int = 0,
    val unmatchedGames: List<UnmatchedGame> = emptyList()
)

class GameRepository(
    private val gameDao: GameDao,
    private val ignoredGameDao: IgnoredGameDao,
    private val wishlistGameDao: WishlistGameDao,
    private val igdbClient: IgdbClient,
    private val steamClient: SteamClient,
    private val gogClient: GogClient,
    private val epicClient: EpicClient,
    private val ubisoftClient: UbisoftClient,
    private val battleNetClient: BattleNetClient,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Syncs games from Battle.net.
     */
    suspend fun syncBattleNetGames(
        cookies: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Fetching Battle.net library...")
        val bnetGames = battleNetClient.fetchOwnedGames(cookies)
        if (bnetGames.isEmpty()) return@withContext SyncResult(0, 0, 0)

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("Battle.net").toSet()
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        igdbClient.authenticate(clientId, clientSecret)

        val localGames = gameDao.getAllGames().first()
        var importedCount = 0
        var alreadyPresentCount = 0
        var ignoredCount = 0
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        bnetGames.forEachIndexed { index, bGame ->
            val storeId = if (bGame.titleId != 0L) bGame.titleId.toString() else bGame.uid ?: bGame.classicGameType ?: bGame.gameName ?: bGame.gameTitle ?: bGame.gameAccountName ?: bGame.name ?: "Unknown"
            if (ignoredIds.contains(storeId)) {
                ignoredCount++
                return@forEachIndexed
            }
            
            val title = bGame.gameName ?: bGame.gameTitle ?: bGame.localizedGameName ?: bGame.localizedName ?: bGame.gameAccountName ?: bGame.name ?: "Unknown Battle.net Game"
            
            val progress = 0.2f + (0.8f * (index.toFloat() / bnetGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching: $title...")

            // 1. Try matching by Source ID
            val existingBySourceId = localGames.find { it.sourceIds["BATTLE_NET"] == storeId }
            if (existingBySourceId != null) {
                val updatedPlatforms = existingBySourceId.platforms.toMutableList().apply { if (!contains("Battle.net")) add("Battle.net") }
                val updatedSourceIds = existingBySourceId.sourceIds.toMutableMap().apply { put("BATTLE_NET", storeId) }
                gameDao.updateGame(existingBySourceId.copy(platforms = updatedPlatforms, sourceIds = updatedSourceIds))
                alreadyPresentCount++
                return@forEachIndexed
            }

            // 2. Try fuzzy matching by Title
            val existingByFuzzyTitle = localGames.find { fuzzyTitleMatch(it.title, title) }
            if (existingByFuzzyTitle != null) {
                val updatedPlatforms = existingByFuzzyTitle.platforms.toMutableList()
                val isAlreadyPresent = updatedPlatforms.contains("Battle.net")
                
                if (!isAlreadyPresent) updatedPlatforms.add("Battle.net")
                val updatedSourceIds = existingByFuzzyTitle.sourceIds.toMutableMap().apply { put("BATTLE_NET", storeId) }
                
                gameDao.updateGame(existingByFuzzyTitle.copy(platforms = updatedPlatforms, sourceIds = updatedSourceIds))
                
                if (isAlreadyPresent) {
                    alreadyPresentCount++
                } else {
                    println("Battle.net Sync: Updated (Fuzzy) $title")
                    importedCount++
                }
                return@forEachIndexed
            }

            // 3. Search IGDB
            val match = findBestIgdbMatch(clientId, title)
            if (match != null) {
                val fullMatch = igdbClient.getGamesByIds(clientId, listOf(match.id)).firstOrNull() ?: match
                val game = Game(
                    title = fullMatch.name,
                    platforms = listOf("Battle.net"),
                    coverImageUrl = getFullCoverUrl(fullMatch.cover?.url),
                    releaseDate = formatTimestamp(fullMatch.firstReleaseDate),
                    igdbId = fullMatch.id,
                    sourceIds = mapOf("BATTLE_NET" to storeId),
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
                    franchises = fullMatch.franchises?.map { it.name } ?: emptyList(),
                    series = (listOfNotNull(fullMatch.collection?.name) + (fullMatch.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                    gameModes = mapIgdbGameModes(fullMatch.gameModes),
                    storeUrls = extractStoreUrls(fullMatch, mapOf("BATTLE_NET" to storeId)),
                    parentIgdbId = fullMatch.parentGame,
                    category = fullMatch.category
                )
                gameDao.insertGame(game)
                println("Battle.net Sync: Imported ${fullMatch.name}")
                importedCount++
            } else {
                val candidates = igdbClient.searchGames(clientId, cleanTitle(title))
                unmatchedGames.add(UnmatchedGame(
                    storeTitle = title,
                    storeId = storeId,
                    platform = "Battle.net",
                    playtimeMinutes = 0,
                    candidates = candidates.take(10)
                ))
            }
        }
        onProgress(1.0f, "Battle.net sync complete!")
        SyncResult(importedCount, alreadyPresentCount, ignoredCount, unmatchedGames)
    }

    /**
     * Syncs games from Epic Games Store.
     */
    suspend fun syncEpicGames(
        code: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Exchanging Epic code...")
        val tokenResponse = epicClient.exchangeCodeForToken(code) ?: return@withContext SyncResult(0, 0, 0)
        
        onProgress(0.2f, "Fetching Epic library...")
        val epicRecords = epicClient.fetchLibraryItems(tokenResponse.accessToken)
        println("Epic Sync: Fetched ${epicRecords.size} library items")
        epicRecords.forEach { record ->
            println("Epic Sync: Found library item - Title: '${record.sandboxName}', Namespace: '${record.namespace}', CatalogItemId: '${record.catalogItemId}'")
        }
        if (epicRecords.isEmpty()) return@withContext SyncResult(0, 0, 0)

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("Epic").toSet()
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        igdbClient.authenticate(clientId, clientSecret)

        val localGames = gameDao.getAllGames().first()
        var importedCount = 0
        var alreadyPresentCount = 0
        var ignoredCount = 0
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        epicRecords.forEachIndexed { index, record ->
            // Skip if ignored
            if (ignoredIds.contains(record.catalogItemId)) {
                println("Epic Sync Filter: Skipping ignored game - CatalogItemId: '${record.catalogItemId}'")
                ignoredCount++
                return@forEachIndexed
            }

            // Skip if recordType is not APPLICATION (e.g., DLCs, add-ons)
            if (record.recordType != "APPLICATION") {
                println("Epic Sync Filter: Skipping non-application record - Type: '${record.recordType}', CatalogItemId: '${record.catalogItemId}'")
                return@forEachIndexed
            }
            
            println("Epic Sync: Processing library item - SandboxName: '${record.sandboxName}', Namespace: '${record.namespace}'")

            val title = record.sandboxName ?: ""
            
            // Skip if sandboxName is blank or looks like an internal ID
            if (title.isBlank() || title.matches(Regex("^[a-f0-9]{32}$"))) {
                println("Epic Sync Filter: Skipping invalid sandboxName '$title' - CatalogItemId: '${record.catalogItemId}'")
                return@forEachIndexed
            }
            
            val progress = 0.2f + (0.8f * (index.toFloat() / epicRecords.size))
            if (index % 5 == 0) onProgress(progress, "Matching: ${record.sandboxName}...")

            // 1. Try matching by Source ID
            val existingBySourceId = localGames.find { it.sourceIds["EPIC"] == record.catalogItemId }
            if (existingBySourceId != null) {
                val updatedPlatforms = existingBySourceId.platforms.toMutableList()
                val alreadyHasPlatform = updatedPlatforms.contains("Epic")
                if (!alreadyHasPlatform) updatedPlatforms.add("Epic")
                
                val updatedSourceIds = existingBySourceId.sourceIds.toMutableMap()
                updatedSourceIds["EPIC"] = record.catalogItemId
                
                gameDao.updateGame(existingBySourceId.copy(
                    platforms = updatedPlatforms,
                    sourceIds = updatedSourceIds
                ))
                if (alreadyHasPlatform) {
                    alreadyPresentCount++
                } else {
                    println("Epic Sync: Updated (Source ID) $title")
                    importedCount++
                }
                return@forEachIndexed
            }

            // 2. Try fuzzy matching by Title
            val existingByFuzzyTitle = localGames.find { fuzzyTitleMatch(it.title, title) }
            if (existingByFuzzyTitle != null) {
                println("Epic Sync: Fuzzy title match found - Title: '$title' (Existing: '${existingByFuzzyTitle.title}')")
                val isAlreadyPresent = existingByFuzzyTitle.platforms.contains("Epic")
                
                val updatedPlatforms = existingByFuzzyTitle.platforms.toMutableList()
                if (!isAlreadyPresent) updatedPlatforms.add("Epic")
                
                val updatedSourceIds = existingByFuzzyTitle.sourceIds.toMutableMap().apply { put("EPIC", record.catalogItemId) }
                
                gameDao.updateGame(existingByFuzzyTitle.copy(platforms = updatedPlatforms, sourceIds = updatedSourceIds))
                
                if (isAlreadyPresent) {
                    alreadyPresentCount++
                } else {
                    println("Epic Sync: Updated (Fuzzy) $title")
                    importedCount++
                }
                return@forEachIndexed
            }

            // 3. Search IGDB
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
                    franchises = fullMatch.franchises?.map { it.name } ?: emptyList(),
                    series = (listOfNotNull(fullMatch.collection?.name) + (fullMatch.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                    gameModes = mapIgdbGameModes(fullMatch.gameModes),
                    storeUrls = extractStoreUrls(fullMatch, mapOf("EPIC" to record.catalogItemId)),
                    parentIgdbId = fullMatch.parentGame,
                    category = fullMatch.category
                )
                gameDao.insertGame(game)
                println("Epic Sync: Imported ${fullMatch.name}")
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

        onProgress(1.0f, "Epic sync complete!")
        SyncResult(importedCount, alreadyPresentCount, ignoredCount, unmatchedGames)
    }

    /**
     * Syncs games from Ubisoft Connect.
     */
    suspend fun syncUbisoftGames(
        ticket: String,
        sessionId: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Fetching Ubisoft library...")
        val ubisoftGames = ubisoftClient.fetchOwnedGames(ticket, sessionId)
        if (ubisoftGames.isEmpty()) return@withContext SyncResult(0, 0, 0)

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("Ubisoft").toSet()
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        igdbClient.authenticate(clientId, clientSecret)

        val localGames = gameDao.getAllGames().first()
        var importedCount = 0
        var alreadyPresentCount = 0
        var ignoredCount = 0
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        ubisoftGames.forEachIndexed { index, uGame ->
            if (ignoredIds.contains(uGame.titleId)) {
                ignoredCount++
                return@forEachIndexed
            }

            val title = uGame.name ?: "Unknown Ubisoft Game"
            val titleId = uGame.titleId
            
            val progress = 0.2f + (0.8f * (index.toFloat() / ubisoftGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching: $title...")

            // 1. Try matching by Source ID
            val existingBySourceId = localGames.find { it.sourceIds["UBISOFT"] == titleId }
            if (existingBySourceId != null) {
                val updatedPlatforms = existingBySourceId.platforms.toMutableList().apply { if (!contains("Ubisoft")) add("Ubisoft") }
                val updatedSourceIds = existingBySourceId.sourceIds.toMutableMap().apply { put("UBISOFT", titleId) }
                gameDao.updateGame(existingBySourceId.copy(platforms = updatedPlatforms, sourceIds = updatedSourceIds))
                println("Ubisoft Sync: Updated (Source ID) $title")
                alreadyPresentCount++
                return@forEachIndexed
            }

            // 2. Try fuzzy matching by Title
            val existingByFuzzyTitle = localGames.find { fuzzyTitleMatch(it.title, title) }
            if (existingByFuzzyTitle != null) {
                val updatedPlatforms = existingByFuzzyTitle.platforms.toMutableList()
                val isAlreadyPresent = updatedPlatforms.contains("Ubisoft")
                
                if (!isAlreadyPresent) updatedPlatforms.add("Ubisoft")
                val updatedSourceIds = existingByFuzzyTitle.sourceIds.toMutableMap().apply { put("UBISOFT", titleId) }
                
                gameDao.updateGame(existingByFuzzyTitle.copy(platforms = updatedPlatforms, sourceIds = updatedSourceIds))
                
                if (isAlreadyPresent) {
                    alreadyPresentCount++
                } else {
                    println("Ubisoft Sync: Updated (Fuzzy) $title")
                    importedCount++
                }
                return@forEachIndexed
            }

            // 3. Search IGDB
            val match = findBestIgdbMatch(clientId, title)
            if (match != null) {
                val fullMatch = igdbClient.getGamesByIds(clientId, listOf(match.id)).firstOrNull() ?: match
                val game = Game(
                    title = fullMatch.name,
                    platforms = listOf("Ubisoft"),
                    coverImageUrl = getFullCoverUrl(fullMatch.cover?.url),
                    releaseDate = formatTimestamp(fullMatch.firstReleaseDate),
                    igdbId = fullMatch.id,
                    sourceIds = mapOf("UBISOFT" to titleId),
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
                    franchises = fullMatch.franchises?.map { it.name } ?: emptyList(),
                    series = (listOfNotNull(fullMatch.collection?.name) + (fullMatch.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                    gameModes = mapIgdbGameModes(fullMatch.gameModes),
                    storeUrls = extractStoreUrls(fullMatch, mapOf("UBISOFT" to titleId)),
                    parentIgdbId = fullMatch.parentGame,
                    category = fullMatch.category
                )
                gameDao.insertGame(game)
                println("Ubisoft Sync: Imported ${fullMatch.name}")
                importedCount++
            } else {
                val candidates = igdbClient.searchGames(clientId, cleanTitle(title))
                unmatchedGames.add(UnmatchedGame(
                    storeTitle = title,
                    storeId = titleId,
                    platform = "Ubisoft",
                    playtimeMinutes = 0,
                    candidates = candidates.take(10)
                ))
            }
        }
        onProgress(1.0f, "Ubisoft sync complete!")
        SyncResult(importedCount, alreadyPresentCount, ignoredCount, unmatchedGames)
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
        val apiKey = settingsRepository.steamApiKey.firstOrNull() ?: return@withContext SyncResult(-1, 0, 0)

        onProgress(0.02f, "Resolving Steam ID...")
        val steamId = steamClient.resolveVanityUrl(apiKey, steamIdInput) ?: return@withContext SyncResult(0, 0, 0)

        onProgress(0.05f, "Fetching games from Steam API...")
        val steamGames = steamClient.fetchOwnedGames(apiKey, steamId)
        if (steamGames.isEmpty()) return@withContext SyncResult(0, 0, 0)

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("Steam").toSet()
        var ignoredCount = 0
        val filteredSteamGames = steamGames.filter { 
            if (it.appid.toString() in ignoredIds) {
                ignoredCount++
                false
            } else true
        }
        if (filteredSteamGames.isEmpty() && ignoredCount == 0) return@withContext SyncResult(0, 0, 0)
        if (filteredSteamGames.isEmpty() && ignoredCount > 0) return@withContext SyncResult(0, 0, ignoredCount)

        // Local Lookup Optimization: Get existing Steam IDs from database
        val localGames = gameDao.getAllGames().first()
        val localSteamToIgdb = localGames.mapNotNull { game ->
            val appId = game.sourceIds["STEAM"]
            val igdbId = game.igdbId
            if (appId != null && igdbId != null) appId to igdbId else null
        }.toMap()

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        igdbClient.authenticate(clientId, clientSecret)

        val igdbIdToSteamIds = mutableMapOf<Long, MutableList<String>>()
        val unknownSteamGames = mutableListOf<SteamClient.SteamGame>()
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        // Separate known from unknown
        filteredSteamGames.forEach { steamGame ->
            val appId = steamGame.appid.toString()
            val knownIgdbId = localSteamToIgdb[appId]
            if (knownIgdbId != null) {
                igdbIdToSteamIds.getOrPut(knownIgdbId) { mutableListOf() }.add(appId)
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
                    igdbIdToSteamIds.getOrPut(it.game!!) { mutableListOf() }.add(it.uid)
                }
            }
        }

        // Step 2: Fallback - Search by Title for unmatched games
        val matchedAppIds = igdbIdToSteamIds.values.flatten().toSet()
        val stillUnknownSteamGames = unknownSteamGames.filter { it.appid.toString() !in matchedAppIds }
        
        stillUnknownSteamGames.forEachIndexed { index, steamGame ->
            val progress = 0.2f + (0.4f * (index.toFloat() / stillUnknownSteamGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching: ${steamGame.name}...")
            
            val match = findBestIgdbMatch(clientId, steamGame.name)
            if (match != null) {
                igdbIdToSteamIds.getOrPut(match.id) { mutableListOf() }.add(steamGame.appid.toString())
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
        val newIgdbIds = igdbIdToSteamIds.keys.filter { it !in localIgdbIds }
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
        var alreadyPresentCount = 0
        val totalToImport = igdbIdToSteamIds.size
        igdbIdToSteamIds.entries.forEachIndexed { index, (igdbId, steamAppIds) ->
            val progress = 0.8f + (0.2f * (index.toFloat() / totalToImport))
            if (index % 10 == 0) onProgress(progress, "Updating library...")

            // Aggregate playtime for all matched AppIDs
            val associatedSteamGames = filteredSteamGames.filter { it.appid.toString() in steamAppIds }
            if (associatedSteamGames.isEmpty()) return@forEachIndexed
            
            val totalPlaytime = associatedSteamGames.sumOf { it.playtime_forever }
            val primaryAppId = steamAppIds.first() // Use first one as main link
            
            val existingGame = gameDao.getGameByIgdbId(igdbId)
            
            if (existingGame != null) {
                // Merging known game (Fast)
                val updatedPlatforms = existingGame.platforms.toMutableList()
                val isAlreadyPresent = updatedPlatforms.contains("Steam")
                
                if (updatedPlatforms.contains("IGDB")) updatedPlatforms.remove("IGDB")
                if (!updatedPlatforms.contains("Steam")) updatedPlatforms.add("Steam")
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                val oldPlaytime = updatedPlaytimes["Steam"] ?: 0
                val isPlaytimeSame = oldPlaytime == totalPlaytime
                
                if (isAlreadyPresent && isPlaytimeSame) {
                    alreadyPresentCount++
                } else {
                    val reason = if (!isAlreadyPresent) "Platform link missing" else "Playtime changed from $oldPlaytime to $totalPlaytime (Merged IDs: ${steamAppIds.joinToString()})"
                    println("Steam Sync: Updated ${existingGame.title} ($reason)")
                    importedCount++
                }

                updatedPlaytimes["Steam"] = totalPlaytime
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["STEAM"] = primaryAppId

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
                    coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame?.cover?.url) ?: existingGame.coverImageUrl),
                    parentIgdbId = igdbGame?.parentGame ?: existingGame.parentIgdbId,
                    category = igdbGame?.category ?: existingGame.category
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
                    sourceIds = mapOf("STEAM" to primaryAppId),
                    playtimes = mapOf("Steam" to totalPlaytime),
                    playtimeMinutes = totalPlaytime,
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
                    franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
                    series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                    gameModes = mapIgdbGameModes(igdbGame.gameModes),
                    storeUrls = extractStoreUrls(igdbGame, mapOf("STEAM" to primaryAppId)).toMutableMap().apply {
                        if (!containsKey("Steam")) put("Steam", "https://store.steampowered.com/app/$primaryAppId")
                    },
                    parentIgdbId = igdbGame.parentGame,
                    category = igdbGame.category
                )
                gameDao.insertGame(game)
                println("Steam Sync: Imported ${igdbGame.name}")
                importedCount++
            }
        }
        onProgress(1.0f, "Import complete!")
        SyncResult(importedCount, alreadyPresentCount, ignoredCount, unmatchedGames)
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
        if (gogGames.isEmpty()) return@withContext SyncResult(0, 0, 0)

        val ignoredIds = ignoredGameDao.getIgnoredIdsByPlatform("GOG").toSet()
        var ignoredCount = 0
        val filteredGogGames = gogGames.filter { 
            if (it.gogId in ignoredIds) {
                ignoredCount++
                false
            } else true
        }
        if (filteredGogGames.isEmpty() && ignoredCount == 0) return@withContext SyncResult(0, 0, 0)
        if (filteredGogGames.isEmpty() && ignoredCount > 0) return@withContext SyncResult(0, 0, ignoredCount)

        // Local Lookup Optimization
        val localGames = gameDao.getAllGames().first()
        val localGogToIgdb = localGames.mapNotNull { game ->
            val gogId = game.sourceIds["GOG"]
            val igdbId = game.igdbId
            if (gogId != null && igdbId != null) gogId to igdbId else null
        }.toMap()

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        igdbClient.authenticate(clientId, clientSecret)

        val igdbIdToGogIds = mutableMapOf<Long, MutableList<String>>()
        val unknownGogGames = mutableListOf<GogClient.GogGame>()
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        filteredGogGames.forEach { gogGame ->
            val knownIgdbId = localGogToIgdb[gogGame.gogId]
            if (knownIgdbId != null) {
                igdbIdToGogIds.getOrPut(knownIgdbId) { mutableListOf() }.add(gogGame.gogId)
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
                    igdbIdToGogIds.getOrPut(it.game!!) { mutableListOf() }.add(it.uid)
                }
            }
        }

        // Step 2: Fallback for games not matched by ID - Search by Title
        val matchedGogIds = igdbIdToGogIds.values.flatten().toSet()
        val stillUnknownGogGames = unknownGogGames.filter { it.gogId !in matchedGogIds }
        
        stillUnknownGogGames.forEachIndexed { index, gogGame ->
            val progress = 0.2f + (0.4f * (index.toFloat() / stillUnknownGogGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching: ${gogGame.title}...")
            
            val match = findBestIgdbMatch(clientId, gogGame.title)
            if (match != null) {
                igdbIdToGogIds.getOrPut(match.id) { mutableListOf() }.add(gogGame.gogId)
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
        val newIgdbIds = igdbIdToGogIds.keys.filter { it !in localIgdbIds }
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
        var alreadyPresentCount = 0
        val totalToImport = igdbIdToGogIds.size
        igdbIdToGogIds.entries.forEachIndexed { index, (igdbId, gogSourceIds) ->
            val progress = 0.8f + (0.2f * (index.toFloat() / totalToImport))
            if (index % 10 == 0) onProgress(progress, "Updating library...")

            val associatedGogGames = filteredGogGames.filter { it.gogId in gogSourceIds }
            if (associatedGogGames.isEmpty()) return@forEachIndexed
            
            val totalPlaytime = associatedGogGames.sumOf { it.playtimeMinutes }
            val primaryGogId = gogSourceIds.first()
            
            val existingGame = gameDao.getGameByIgdbId(igdbId)
            
            if (existingGame != null) {
                val updatedPlatforms = existingGame.platforms.toMutableList()
                val isAlreadyPresent = updatedPlatforms.contains("GOG")
                
                if (!isAlreadyPresent) updatedPlatforms.add("GOG")
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                val oldPlaytime = updatedPlaytimes["GOG"] ?: 0
                val isPlaytimeSame = oldPlaytime == totalPlaytime
                
                if (isAlreadyPresent && isPlaytimeSame) {
                    alreadyPresentCount++
                } else {
                    val reason = if (!isAlreadyPresent) "Platform link missing" else "Playtime changed from $oldPlaytime to $totalPlaytime"
                    println("Gog Sync: Updated ${existingGame.title} ($reason)")
                    importedCount++
                }

                updatedPlaytimes["GOG"] = totalPlaytime
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["GOG"] = primaryGogId

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
                    coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame?.cover?.url) ?: existingGame.coverImageUrl),
                    parentIgdbId = igdbGame?.parentGame ?: existingGame.parentIgdbId,
                    category = igdbGame?.category ?: existingGame.category
                ))
            } else {
                val igdbGame = igdbGamesMetadata[igdbId] ?: return@forEachIndexed
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf("GOG"),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = normalizeDate(formatTimestamp(igdbGame.firstReleaseDate)),
                    igdbId = igdbId,
                    sourceIds = mapOf("GOG" to primaryGogId),
                    playtimes = mapOf("GOG" to totalPlaytime),
                    playtimeMinutes = totalPlaytime,
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
                    franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
                    series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                    gameModes = mapIgdbGameModes(igdbGame.gameModes),
                    storeUrls = extractStoreUrls(igdbGame, mapOf("GOG" to primaryGogId)),
                    parentIgdbId = igdbGame.parentGame,
                    category = igdbGame.category
                )
                gameDao.insertGame(game)
                println("Gog Sync: Imported ${igdbGame.name}")
                importedCount++
            }
        }
        onProgress(1.0f, "Import complete!")
        SyncResult(importedCount, alreadyPresentCount, ignoredCount, unmatchedGames)
    }

    /**
     * Syncs games from a Playnite JSON export.
     */
    suspend fun syncPlayniteGames(
        playniteGames: List<PlayniteGame>,
        statusMapping: Map<String, CompletionStatus>,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): SyncResult = withContext(Dispatchers.IO) {
        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext SyncResult(0, 0, 0)
        igdbClient.authenticate(clientId, clientSecret)

        val localGames = gameDao.getAllGames().first()
        val igdbIdToPlayniteGames = mutableMapOf<Long, MutableList<PlayniteGame>>()
        val unmatchedGames = mutableListOf<UnmatchedGame>()

        playniteGames.forEachIndexed { index, pGame ->
            val progress = 0.1f + (0.7f * (index.toFloat() / playniteGames.size))
            if (index % 10 == 0) onProgress(progress, "Matching: ${pGame.name}...")

            val match = findBestIgdbMatch(clientId, pGame.name)
            if (match != null) {
                igdbIdToPlayniteGames.getOrPut(match.id) { mutableListOf() }.add(pGame)
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
        val newIgdbIds = igdbIdToPlayniteGames.keys.filter { it !in localIgdbIds }
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
        var alreadyPresentCount = 0
        val ignoredCount = 0
        val entries = igdbIdToPlayniteGames.entries.toList()
        entries.forEachIndexed { index, (igdbId, pGames) ->
            val progress = 0.9f + (0.1f * (index.toFloat() / entries.size))
            if (index % 10 == 0) onProgress(progress, "Updating library...")

            // Aggregate playtime and find best platform name
            val totalPlaytimeMin = pGames.sumOf { (it.playtime / 60).toInt() }
            val sourcePlatform = pGames.firstNotNullOfOrNull { it.source?.name } ?: "Playnite"
            val status = pGames.firstNotNullOfOrNull { it.completionStatus?.name }?.let { statusMapping[it] } ?: CompletionStatus.BACKLOG

            val existingGame = gameDao.getGameByIgdbId(igdbId)

            if (existingGame != null) {
                val updatedPlatforms = existingGame.platforms.toMutableList()
                val isAlreadyPresent = updatedPlatforms.contains(sourcePlatform)
                
                if (!isAlreadyPresent) updatedPlatforms.add(sourcePlatform)
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                updatedPlaytimes[sourcePlatform] = totalPlaytimeMin
                
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
                    releaseDate = if (existingGame.isReleaseDateManual) existingGame.releaseDate else normalizeDate(pGames.first().releaseDate?.releaseDate),
                    storeUrls = updatedStoreUrls,
                    genres = if (existingGame.isGenreManual) existingGame.genres else (igdbGame?.genres?.map { it.name } ?: existingGame.genres),
                    gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame?.gameModes),
                    coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame?.cover?.url) ?: existingGame.coverImageUrl),
                    parentIgdbId = igdbGame?.parentGame ?: existingGame.parentIgdbId,
                    category = igdbGame?.category ?: existingGame.category
                ))
                
                if (isAlreadyPresent) {
                    alreadyPresentCount++
                } else {
                    println("Playnite Sync: Updated (Fuzzy) ${existingGame.title}")
                    importedCount++
                }
            } else {
                val igdbGame = igdbGamesMetadata[igdbId] ?: return@forEachIndexed
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf(sourcePlatform),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = normalizeDate(pGames.first().releaseDate?.releaseDate) ?: formatTimestamp(igdbGame.firstReleaseDate),
                    igdbId = igdbId,
                    playtimes = mapOf(sourcePlatform to totalPlaytimeMin),
                    playtimeMinutes = totalPlaytimeMin,
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
                    franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
                    series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                    gameModes = mapIgdbGameModes(igdbGame.gameModes),
                    storeUrls = extractStoreUrls(igdbGame, emptyMap()),
                    parentIgdbId = igdbGame.parentGame,
                    category = igdbGame.category
                )
                gameDao.insertGame(game)
                println("Playnite Sync: Imported ${igdbGame.name}")
                importedCount++
            }
        }

        onProgress(1.0f, "Playnite import complete!")
        SyncResult(importedCount, alreadyPresentCount, ignoredCount, unmatchedGames)
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
        val sourceKey = when (unmatchedGame.platform) {
            "Steam" -> "STEAM"
            "GOG" -> "GOG"
            "Epic" -> "EPIC"
            "Ubisoft" -> "UBISOFT"
            else -> unmatchedGame.platform.uppercase()
        }

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
                coverImageUrl = if (existingGame.isCoverManual) existingGame.coverImageUrl else (getFullCoverUrl(igdbGame.cover?.url) ?: existingGame.coverImageUrl),
                parentIgdbId = igdbGame.parentGame ?: existingGame.parentIgdbId,
                category = igdbGame.category
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
                franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
                series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                gameModes = mapIgdbGameModes(igdbGame.gameModes),
                storeUrls = extractStoreUrls(igdbGame, mapOf(sourceKey to unmatchedGame.storeId)),
                parentIgdbId = igdbGame.parentGame,
                category = igdbGame.category
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
        val romanToNumeric = mapOf(
            "viii" to "8", "vii" to "7", "iii" to "3", "vi" to "6", "iv" to "4", "ix" to "9", "ii" to "2", "v" to "5"
        )
        
        var romanTitle = cleanedTitle.lowercase()
        romanToNumeric.forEach { (roman, numeric) ->
            romanTitle = romanTitle.replace(" $roman", " $numeric")
        }
        
        if (romanTitle != cleanedTitle.lowercase()) {
            val stage3 = igdbClient.searchGames(clientId, romanTitle)
            stage3.filter { cleanTitle(it.name).lowercase().contains(cleanTitle(romanTitle)) }.pickBest()?.let { return it }
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
        
        var parentId = igdbGame.parentGame
        if (parentId == null && igdbGame.category == 3) {
            parentId = igdbClient.getParentForBundle(clientId, igdbGame.id)?.id
        }

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
            franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
            series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
            gameModes = if (existingGame.isGameModeManual) existingGame.gameModes else mapIgdbGameModes(igdbGame.gameModes),
            storeUrls = extractStoreUrls(igdbGame, existingGame.sourceIds),
            parentIgdbId = parentId,
            category = igdbGame.category
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
        if (game.platforms.contains("Ubisoft") && game.sourceIds.containsKey("UBISOFT")) {
            ignoredGameDao.insertIgnoredGame(IgnoredGame(title = game.title, platform = "Ubisoft", catalogItemId = game.sourceIds["UBISOFT"]!!))
        }
        if (game.platforms.contains("Battle.net") && game.sourceIds.containsKey("BATTLE_NET")) {
            ignoredGameDao.insertIgnoredGame(IgnoredGame(title = game.title, platform = "Battle.net", catalogItemId = game.sourceIds["BATTLE_NET"]!!))
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

    fun getGameByIdFlow(id: Int): Flow<Game?> {
        return gameDao.getGameByIdFlow(id)
    }

    suspend fun getGameByTitle(title: String): Game? {
        return gameDao.getGameByTitle(title)
    }

    suspend fun getGameByIgdbId(igdbId: Long): Game? {
        return gameDao.getGameByIgdbId(igdbId)
    }

    fun getGameByIgdbIdFlow(igdbId: Long): Flow<Game?> {
        return gameDao.getGameByIgdbIdFlow(igdbId)
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
        if (timestamp in 1901..2099) {
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
        if (dateStr.isNullOrBlank()) return null
        
        val trimmed = dateStr.trim()
        
        // Handle YYYY
        if (trimmed.length == 4 && trimmed.all { it.isDigit() }) {
            return "$trimmed-01-01"
        }
        
        // Handle YYYY-M-D and variants
        val parts = trimmed.split("-", "/", ".")
        if (parts.size == 3) {
            // Check if year is first (YYYY-MM-DD) or last (DD.MM.YYYY)
            val p0 = parts[0]
            val p2 = parts[2]
            
            return if (p0.length == 4) {
                // YYYY-MM-DD
                val y = p0
                val m = parts[1].padStart(2, '0')
                val d = p2.padStart(2, '0')
                "$y-$m-$d"
            } else if (p2.length == 4) {
                // DD-MM-YYYY
                val y = p2
                val m = parts[1].padStart(2, '0')
                val d = p0.padStart(2, '0')
                "$y-$m-$d"
            } else {
                // Fallback to existing logic if ambiguous
                val y = p0.padStart(4, '2').takeLast(4)
                val m = parts[1].padStart(2, '0')
                val d = p2.padStart(2, '0')
                "$y-$m-$d"
            }
        }
        
        return trimmed
    }

    /**
     * Generates a JSON string representing the entire game collection.
     */
    suspend fun generateJsonContent(): String = withContext(Dispatchers.IO) {
        val games = gameDao.getAllGames().first()
        val ignoredGames = ignoredGameDao.getAllIgnoredGames().first()
        val backup = BackupData(games = games, ignoredGames = ignoredGames)
        val json = Json { 
            prettyPrint = true
            encodeDefaults = true 
        }
        json.encodeToString(backup)
    }

    /**
     * Imports games from a JSON string.
     */
    suspend fun importFromJson(jsonString: String) = withContext(Dispatchers.IO) {
        if (jsonString.isBlank()) {
            throw IllegalArgumentException("Import failed: The selected file is empty.")
        }
        
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        
        val backup = try {
            json.decodeFromString<BackupData>(jsonString)
        } catch (e: Exception) {
            // Legacy format: just a list of games
            try {
                val games: List<Game> = json.decodeFromString(jsonString)
                BackupData(games = games)
            } catch (e2: Exception) {
                BackupData() // Empty or malformed
            }
        }

        val localGames = gameDao.getAllGames().first()
        
        println("Importing ${backup.games.size} games and ${backup.ignoredGames.size} ignored games.")

        // Deduplicate: Map imported games to existing local IDs where possible
        val mergedGames = backup.games.map { imported ->
            val existing = localGames.find { local ->
                (imported.igdbId != null && local.igdbId == imported.igdbId) ||
                (local.title.equals(imported.title, ignoreCase = true))
            }
            
            val normalizedImported = imported.copy(
                releaseDate = normalizeDate(imported.releaseDate)
            )

            if (existing != null) {
                // Keep the database ID of the existing record to trigger an UPDATE instead of an INSERT
                normalizedImported.copy(id = existing.id)
            } else {
                // No match found, insert as a new game
                normalizedImported.copy(id = 0)
            }
        }
        
        if (mergedGames.isNotEmpty()) {
            gameDao.insertGames(mergedGames)
        }

        // Restore ignored games
        if (backup.ignoredGames.isNotEmpty()) {
            val existingIgnored = ignoredGameDao.getAllIgnoredGames().first()
            val filteredIgnored = backup.ignoredGames.map { it.copy(id = 0) }.filter { imported ->
                existingIgnored.none { it.platform == imported.platform && it.catalogItemId == imported.catalogItemId }
            }
            
            if (filteredIgnored.isNotEmpty()) {
                ignoredGameDao.insertIgnoredGames(filteredIgnored)
            }
        }
    }

    /**
     * Wipes the entire game collection.
     */
    suspend fun clearLibrary() = withContext(Dispatchers.IO) {
        gameDao.deleteAllGames()
    }

    // ==================== WISHLIST ====================

    fun getAllWishlistGames(): Flow<List<WishlistGame>> = wishlistGameDao.getAllWishlistGames()

    suspend fun addWishlistGame(game: WishlistGame) {
        wishlistGameDao.insertWishlistGame(game)
    }

    suspend fun updateWishlistGame(game: WishlistGame) {
        wishlistGameDao.updateWishlistGame(game)
    }

    suspend fun deleteWishlistGame(game: WishlistGame) {
        wishlistGameDao.deleteWishlistGame(game)
    }

    suspend fun getWishlistGameByIgdbId(igdbId: Long): WishlistGame? {
        return wishlistGameDao.getWishlistGameByIgdbId(igdbId)
    }

    fun getDlcForGame(parentIgdbId: Long): Flow<List<Game>> {
        return gameDao.getDlcForGame(parentIgdbId)
    }

    fun getWishlistDlcForGame(parentIgdbId: Long): Flow<List<WishlistGame>> {
        return wishlistGameDao.getWishlistDlcForGame(parentIgdbId)
    }

    /**
     * Creates a wishlist entry from an IGDB search result.
     * Collects all available store links (Steam, GOG, Epic) and fetches their current EUR prices.
     */
    suspend fun createWishlistEntryFromIgdb(igdbGame: IgdbGame): WishlistGame = withContext(Dispatchers.IO) {
        WishlistGame(
            title = igdbGame.name,
            coverImageUrl = igdbGame.cover?.url?.let { getFullCoverUrl(it) },
            igdbId = igdbGame.id,
            parentIgdbId = igdbGame.parentGame,
            category = igdbGame.category,
            platformPrices = buildPlatformPrices(igdbGame)
        )
    }

    /**
     * Re-fetches store prices for an existing wishlist entry (Steam/GOG only).
     * If the entry has no store entries at all (e.g. created before store resolution was fixed),
     * the store links are re-resolved from IGDB first.
     */
    suspend fun refreshWishlistPrices(game: WishlistGame): WishlistGame = withContext(Dispatchers.IO) {
        val igdbId = game.igdbId
        if (igdbId != null) {
            // Full re-resolve: stores may have been added to IGDB/GOG since the entry was created
            // (e.g. GOG entries often lag behind for brand-new releases).
            val clientId = settingsRepository.clientId.firstOrNull()
            val clientSecret = settingsRepository.clientSecret.firstOrNull()
            if (clientId != null && clientSecret != null) {
                igdbClient.authenticate(clientId, clientSecret)
                val igdbGame = igdbClient.getGamesByIds(clientId, listOf(igdbId)).firstOrNull()
                if (igdbGame != null) {
                    val rebuilt = buildPlatformPrices(igdbGame)
                    if (rebuilt.isNotEmpty()) {
                        println("Wishlist Refresh: Rebuilt '${game.title}' with ${rebuilt.size} stores")
                        return@withContext game.copy(platformPrices = rebuilt)
                    }
                }
            }
        }
        // Fallback: keep the known stores and only refresh their prices
        game.copy(platformPrices = game.platformPrices.map { refreshPlatformPrice(it) })
    }

    /**
     * Builds platform price entries for all stores we can resolve from IGDB external game data.
     * Steam and GOG provide public price APIs; Epic gets a link only (no public price API).
     *
     * NOTE: IGDB no longer populates the `category` field reliably on external_games entries
     * (newer entries are returned without it), so stores are resolved by URL pattern first
     * and only fall back to the category enum.
     */
    private suspend fun buildPlatformPrices(igdbGame: IgdbGame): List<PlatformPrice> {
        // Nested external_games in search results are truncated by the IGDB API and often miss
        // store entries (e.g. Steam). Query the dedicated endpoint for the full list instead.
        val clientId = settingsRepository.clientId.firstOrNull()
        val externals = if (clientId != null) {
            val fetched = igdbClient.getExternalGamesForGame(clientId, igdbGame.id)
            if (fetched.isNotEmpty()) fetched else (igdbGame.externalGames ?: emptyList())
        } else {
            igdbGame.externalGames ?: emptyList()
        }
        val prices = mutableListOf<PlatformPrice>()
        println("Wishlist Stores: '${igdbGame.name}' externals: ${externals.map { "cat=${it.category} url=${it.url}" }}")

        // Finds the first external entry whose URL matches one of the given patterns,
        // or whose category matches (fallback for older IGDB entries that have a category).
        fun findExternal(urlPatterns: List<String>, category: Int?): IgdbExternalGameData? {
            return externals.find { ext ->
                urlPatterns.any { ext.url?.contains(it, ignoreCase = true) == true }
            } ?: externals.find { category != null && it.category == category }
        }

        // Steam: uid/url contain the AppID -> public appdetails API (cc=de => EUR)
        val steamExternal = findExternal(listOf("store.steampowered.com"), IgdbExternalCategory.STEAM)
        val steamAppId = steamExternal?.url?.let { Regex("/app/(\\d+)").find(it)?.groupValues?.get(1) }
            ?: steamExternal?.uid?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
        if (steamAppId != null) {
            val priceInfo = steamClient.fetchCurrentPrice(steamAppId)
            prices.add(PlatformPrice(
                platform = "Steam",
                storeUrl = "https://store.steampowered.com/app/$steamAppId",
                price = priceInfo?.currentPrice ?: 0.0,
                isOnSale = priceInfo?.isOnSale == true,
                originalPrice = priceInfo?.originalPrice,
                externalId = steamAppId
            ))
        }

        // GOG: prefer the IGDB entry; if IGDB has none (common for brand-new releases whose
        // IGDB store data lags behind), search GOG's public catalog by title.
        val gogExternal = findExternal(listOf("gog.com"), IgdbExternalCategory.GOG)
        var gogProductId = gogExternal?.uid?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
            ?: gogExternal?.url?.substringAfterLast("/")?.takeIf { it.isNotBlank() && it.all { c -> c.isDigit() } }
        var gogStoreUrl = gogExternal?.url ?: ""

        if (gogProductId == null && gogStoreUrl.isBlank()) {
            val candidates = gogClient.searchProduct(igdbGame.name)
            if (candidates.isNotEmpty()) {
                fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
                val target = normalize(igdbGame.name)
                val best = candidates.find { normalize(it.slug) == target }
                    ?: candidates.find { val s = normalize(it.slug); s.contains(target) || target.contains(s) }
                    ?: candidates.find { candidate ->
                        // Fallback: Check if title and target share a significant word (>= 4 chars)
                        val candidateWords = normalize(candidate.title).windowed(4, 1)
                        val targetWords = target.windowed(4, 1)
                        candidateWords.any { it in targetWords }
                    }
                if (best != null) {
                    gogProductId = best.productId
                    gogStoreUrl = "https://www.gog.com/en/game/${best.slug}"
                    println("Wishlist Stores: GOG fallback search matched '${best.title}' (id=${best.productId}) for '${igdbGame.name}'")
                }
            }
        }

        if (gogStoreUrl.isNotBlank()) {
            val priceInfo = gogProductId?.let { gogClient.fetchProductPrice(it) }
            prices.add(PlatformPrice(
                platform = "GOG",
                storeUrl = gogStoreUrl,
                price = priceInfo?.currentPrice ?: 0.0,
                isOnSale = priceInfo?.isOnSale == true,
                originalPrice = priceInfo?.originalPrice,
                externalId = gogProductId
            ))
        }

        // Epic Games Store
        val epicUrl = findExternal(
            listOf("epicgames.com"),
            IgdbExternalCategory.EPIC_GAMES
        )?.url?.takeIf { it.isNotBlank() }
        if (epicUrl != null) {
            val slug = epicUrl.substringAfter("/p/").substringBefore("?").removeSuffix("/")
            val priceInfo = if (slug.isNotBlank()) epicClient.fetchProductPrice(slug) else null
            prices.add(PlatformPrice(
                platform = "Epic",
                storeUrl = epicUrl,
                price = priceInfo?.currentPrice ?: 0.0,
                isOnSale = priceInfo?.isOnSale == true,
                originalPrice = priceInfo?.originalPrice,
                externalId = slug.takeIf { it.isNotBlank() }
            ))
        }

        return prices
    }

    private suspend fun refreshPlatformPrice(platformPrice: PlatformPrice): PlatformPrice {
        val priceInfo = when (platformPrice.platform) {
            "Steam" -> platformPrice.externalId?.let { steamClient.fetchCurrentPrice(it) }
                ?.let { Triple(it.currentPrice, it.isOnSale, it.originalPrice) }
            "GOG" -> platformPrice.externalId?.let { gogClient.fetchProductPrice(it) }
                ?.let { Triple(it.currentPrice, it.isOnSale, it.originalPrice) }
            "Epic" -> platformPrice.externalId?.let { epicClient.fetchProductPrice(it) }
                ?.let { Triple(it.currentPrice, it.isOnSale, it.originalPrice) }
            else -> null
        } ?: return platformPrice

        return platformPrice.copy(
            price = priceInfo.first,
            isOnSale = priceInfo.second,
            originalPrice = priceInfo.third,
            lastUpdated = System.currentTimeMillis()
        )
    }

    /**
     * Fetches missing metadata (summary, screenshots, url) for a game if not already present.
     */
    suspend fun refreshGameMetadata(gameId: Int) = withContext(Dispatchers.IO) {
        val existing = gameDao.getGameById(gameId) ?: return@withContext
        val igdbId = existing.igdbId ?: return@withContext

        // Refresh if missing summary OR screenshots OR companies OR store links OR game modes OR franchises OR series
        // OR if it's a DLC/Bundle and we don't have a parent link yet.
        val needsRefresh = existing.summary.isNullOrBlank() || 
                           existing.screenshotUrls.isEmpty() || 
                           existing.developers.isEmpty() ||
                           existing.storeUrls.isEmpty() ||
                           existing.gameModes.isEmpty() ||
                           existing.franchises.isEmpty() ||
                           existing.series.isEmpty() ||
                           existing.category == null ||
                           (existing.category in 1..3 && existing.parentIgdbId == null)

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
            Log.d("GameRepository", "Enrichment Failed: No game found on IGDB for ID $igdbId")
            return@withContext
        }

        // Handle bundles that don't have a parent_game link but are linked from a main game via 'bundles' list.
        var parentId = igdbGame.parentGame
        if (parentId == null && igdbGame.category == 3) { // 3 = Bundle
            parentId = igdbClient.getParentForBundle(clientId, igdbId)?.id
            if (parentId != null) println("Enrichment: Found parent $parentId for bundle $igdbId")
        }

        // Fetch the absolute latest version of the game from DB right before updating
        // to avoid overwriting user changes made while we were fetching metadata.
        val latestExisting = gameDao.getGameById(gameId) ?: return@withContext

        val updatedGame = latestExisting.copy(
            summary = igdbGame.summary,
            screenshotUrls = igdbGame.screenshots?.map { getFullScreenshotUrl(it.url) } ?: emptyList(),
            igdbUrl = igdbGame.url,
            userRating = igdbGame.rating,
            criticRating = igdbGame.aggregatedRating,
            developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
            publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
            themes = igdbGame.themes?.map { it.name } ?: emptyList(),
            keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
            franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
            series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
            gameModes = if (latestExisting.isGameModeManual) latestExisting.gameModes else mapIgdbGameModes(igdbGame.gameModes),
            genres = if (latestExisting.isGenreManual) latestExisting.genres else (igdbGame.genres?.map { it.name } ?: latestExisting.genres),
            storeUrls = extractStoreUrls(igdbGame, latestExisting.sourceIds),
            parentIgdbId = parentId,
            category = igdbGame.category
        )
        
        gameDao.updateGame(updatedGame)

        // Reverse enrichment: update any existing games in the library that are listed as DLCs/Bundles of THIS game.
        val childIds = (igdbGame.dlcs ?: emptyList()) + 
                       (igdbGame.expansions ?: emptyList()) + 
                       (igdbGame.bundles ?: emptyList()) + 
                       (igdbGame.standaloneExpansions ?: emptyList())
        
        if (childIds.isNotEmpty()) {
            val localGames = gameDao.getAllGames().first()
            localGames.filter { it.igdbId != null && childIds.contains(it.igdbId) && it.parentIgdbId == null }
                .forEach { child ->
                    gameDao.updateGame(child.copy(parentIgdbId = igdbId))
                    println("Enrichment: Linked child ${child.title} to parent ${latestExisting.title}")
                }
        }

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
        // Replace Roman numerals with numeric equivalents - SORTED BY LENGTH DESCENDING
        val romanToNumeric = mapOf(
            "viii" to "8", "vii" to "7", "iii" to "3", "vi" to "6", "iv" to "4", "ix" to "9", "ii" to "2", "v" to "5"
        )
        
        fun normalize(t: String): String {
            var res = t.lowercase()
                .replace("®", "")
                .replace("™", "")
                .replace("©", "")
                .replace("&", "and")
            
            // Apply Roman Numeral replacements in specific order
            romanToNumeric.forEach { (roman, numeric) ->
                res = res.replace(roman, numeric)
            }
            
            return res.replace(Regex("[^a-z0-9]"), "")
        }
        
        return normalize(existingTitle) == normalize(newTitle)
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

        // Ubisoft Connect (Category 34)
        val ubiUrl = igdbGame.externalGames?.find { it.category == IgdbExternalCategory.UBISOFT_CONNECT }?.url
        if (ubiUrl != null) {
            urls["Ubisoft"] = ubiUrl
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
