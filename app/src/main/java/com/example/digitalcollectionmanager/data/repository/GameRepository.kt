package com.example.digitalcollectionmanager.data.repository

import com.example.digitalcollectionmanager.data.api.GogClient
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.api.SteamClient
import com.example.digitalcollectionmanager.data.api.models.IgdbExternalCategory
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.dao.GameDao
import com.example.digitalcollectionmanager.data.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

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
    ): Int = withContext(Dispatchers.IO) {
        val apiKey = settingsRepository.steamApiKey.firstOrNull() ?: return@withContext -1

        onProgress(0.02f, "Resolving Steam ID...")
        val steamId = steamClient.resolveVanityUrl(apiKey, steamIdInput) ?: return@withContext 0

        onProgress(0.05f, "Fetching games from Steam API...")
        val steamGames = steamClient.fetchOwnedGames(apiKey, steamId)
        if (steamGames.isEmpty()) return@withContext 0
        println("Steam Sync: Found ${steamGames.size} games on Steam")

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext 0
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext 0
        igdbClient.authenticate(clientId, clientSecret)

        // Step 1: Resolve Steam IDs to IGDB IDs in batches of 50
        val allSteamIds = steamGames.map { it.appid.toString() }
        val igdbIdToSteamId = mutableMapOf<Long, String>()
        
        val batches = allSteamIds.chunked(50)
        batches.forEachIndexed { index, batch ->
            val progress = 0.1f + (0.2f * (index.toFloat() / batches.size))
            onProgress(progress, "Resolving Steam IDs on IGDB (batch ${index + 1}/${batches.size})...")
            
            val externalGames = igdbClient.resolveExternalGames(clientId, IgdbExternalCategory.STEAM, batch)
            externalGames.filter { it.game != null }.forEach { 
                igdbIdToSteamId[it.game!!] = it.uid 
            }
        }
        println("Steam Sync: Matched ${igdbIdToSteamId.size} games by AppID")

        // Step 2: Fallback - Search by Title for unmatched games
        val matchedAppIds = igdbIdToSteamId.values.toSet()
        val unmatchedSteamGames = steamGames.filter { it.appid.toString() !in matchedAppIds }
        
        unmatchedSteamGames.forEachIndexed { index, steamGame ->
            val progress = 0.3f + (0.3f * (index.toFloat() / unmatchedSteamGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching remaining Steam games by title ($index/${unmatchedSteamGames.size})...")
            
            val results = igdbClient.searchGames(clientId, steamGame.name)
            val match = results.find { it.name.equals(steamGame.name, ignoreCase = true) } ?: results.firstOrNull()
            if (match != null) {
                igdbIdToSteamId[match.id] = steamGame.appid.toString()
            }
        }
        println("Steam Sync: Total matched games after title fallback: ${igdbIdToSteamId.size}")
        
        // Step 3: Fetch full metadata
        val igdbIds = igdbIdToSteamId.keys.toList()
        val igdbGames = mutableMapOf<Long, IgdbGame>()
        
        val metadataBatches = igdbIds.chunked(50)
        metadataBatches.forEachIndexed { index, batch ->
            val progress = 0.6f + (0.2f * (index.toFloat() / metadataBatches.size))
            onProgress(progress, "Fetching metadata from IGDB...")
            
            igdbClient.getGamesByIds(clientId, batch).forEach {
                igdbGames[it.id] = it
            }
        }

        // Step 4: Save / Merge
        var importedCount = 0
        val totalToImport = igdbIdToSteamId.size
        igdbIdToSteamId.entries.forEachIndexed { index, (igdbId, steamAppId) ->
            val progress = 0.8f + (0.2f * (index.toFloat() / totalToImport))
            if (index % 10 == 0) onProgress(progress, "Saving to collection...")

            val steamGame = steamGames.find { it.appid.toString() == steamAppId } ?: return@forEachIndexed
            val igdbGame = igdbGames[igdbId] ?: return@forEachIndexed
            
            val playtimeMinutes = steamGame.playtime_forever
            
            val existingGame = gameDao.getGameByIgdbId(igdbId)
            if (existingGame != null) {
                val updatedPlatforms = existingGame.platforms.toMutableList()
                if (updatedPlatforms.contains("IGDB")) updatedPlatforms.remove("IGDB")
                if (!updatedPlatforms.contains("Steam")) updatedPlatforms.add("Steam")
                
                val updatedPlaytimes = existingGame.playtimes.toMutableMap()
                updatedPlaytimes["Steam"] = playtimeMinutes
                
                val updatedSourceIds = existingGame.sourceIds.toMutableMap()
                updatedSourceIds["STEAM"] = steamAppId
                
                val totalPlaytime = updatedPlaytimes.values.sum()
                
                gameDao.updateGame(existingGame.copy(
                    platforms = updatedPlatforms,
                    playtimes = updatedPlaytimes,
                    sourceIds = updatedSourceIds,
                    playtimeMinutes = totalPlaytime,
                    releaseDate = formatTimestamp(igdbGame.firstReleaseDate) // Repair date if needed
                ))
            } else {
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf("Steam"),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = formatTimestamp(igdbGame.firstReleaseDate),
                    igdbId = igdbId,
                    sourceIds = mapOf("STEAM" to steamAppId),
                    playtimes = mapOf("Steam" to playtimeMinutes),
                    playtimeMinutes = playtimeMinutes,
                    genres = igdbGame.genres?.map { it.name } ?: emptyList()
                )
                gameDao.insertGame(game)
                importedCount++
            }
        }
        onProgress(1.0f, "Import complete!")
        importedCount
    }

    /**
     * Syncs games from a public GOG profile.
     */
    suspend fun syncGogGames(
        username: String,
        onProgress: (progress: Float, message: String) -> Unit = { _, _ -> }
    ): Int = withContext(Dispatchers.IO) {
        onProgress(0.05f, "Fetching games from GOG...")
        val gogGames = gogClient.fetchPublicGames(username)
        if (gogGames.isEmpty()) return@withContext 0
        println("GOG Sync: Found ${gogGames.size} games in total from GOG scraping")

        val clientId = settingsRepository.clientId.firstOrNull() ?: return@withContext 0
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return@withContext 0
        igdbClient.authenticate(clientId, clientSecret)

        // Step 1: Resolve GOG IDs to IGDB IDs in batches
        val allGogIds = gogGames.map { it.gogId }
        val igdbIdToGogId = mutableMapOf<Long, String>()
        
        val batches = allGogIds.chunked(50)
        batches.forEachIndexed { index, batch ->
            val progress = 0.1f + (0.2f * (index.toFloat() / batches.size))
            onProgress(progress, "Resolving GOG IDs on IGDB (batch ${index + 1}/${batches.size})...")
            
            val externalGames = igdbClient.resolveExternalGames(clientId, IgdbExternalCategory.GOG, batch)
            externalGames.filter { it.game != null }.forEach { 
                igdbIdToGogId[it.game!!] = it.uid 
            }
        }
        println("GOG Sync: Matched ${igdbIdToGogId.size} games by GOG ID")

        // Step 2: Fallback for games not matched by ID - Search by Title
        val matchedGogIds = igdbIdToGogId.values.toSet()
        val unmatchedGogGames = gogGames.filter { it.gogId !in matchedGogIds }
        
        unmatchedGogGames.forEachIndexed { index, gogGame ->
            val progress = 0.3f + (0.3f * (index.toFloat() / unmatchedGogGames.size))
            if (index % 5 == 0) onProgress(progress, "Matching remaining GOG games by title ($index/${unmatchedGogGames.size})...")
            
            val results = igdbClient.searchGames(clientId, gogGame.title)
            val match = results.find { it.name.equals(gogGame.title, ignoreCase = true) } ?: results.firstOrNull()
            if (match != null) {
                igdbIdToGogId[match.id] = gogGame.gogId
            }
        }
        println("GOG Sync: Total matched games after title fallback: ${igdbIdToGogId.size}")

        // Step 3: Fetch full metadata
        val igdbIds = igdbIdToGogId.keys.toList()
        val igdbGames = mutableMapOf<Long, IgdbGame>()
        
        val metadataBatches = igdbIds.chunked(50)
        metadataBatches.forEachIndexed { index, batch ->
            val progress = 0.6f + (0.2f * (index.toFloat() / metadataBatches.size))
            onProgress(progress, "Fetching metadata from IGDB...")
            
            igdbClient.getGamesByIds(clientId, batch).forEach {
                igdbGames[it.id] = it
            }
        }

        // Step 4: Save / Merge
        var importedCount = 0
        val totalToImport = igdbIdToGogId.size
        igdbIdToGogId.entries.forEachIndexed { index, (igdbId, gogSourceId) ->
            val progress = 0.8f + (0.2f * (index.toFloat() / totalToImport))
            if (index % 10 == 0) onProgress(progress, "Saving to collection...")

            val gogGame = gogGames.find { it.gogId == gogSourceId } ?: return@forEachIndexed
            val igdbGame = igdbGames[igdbId] ?: return@forEachIndexed
            
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
                    releaseDate = formatTimestamp(igdbGame.firstReleaseDate) // Repair date if needed
                ))
            } else {
                val game = Game(
                    title = igdbGame.name,
                    platforms = listOf("GOG"),
                    coverImageUrl = getFullCoverUrl(igdbGame.cover?.url),
                    releaseDate = formatTimestamp(igdbGame.firstReleaseDate),
                    igdbId = igdbId,
                    sourceIds = mapOf("GOG" to gogSourceId),
                    playtimes = mapOf("GOG" to gogGame.playtimeMinutes),
                    playtimeMinutes = gogGame.playtimeMinutes,
                    genres = igdbGame.genres?.map { it.name } ?: emptyList()
                )
                gameDao.insertGame(game)
                importedCount++
            }
        }
        onProgress(1.0f, "Import complete!")
        importedCount
    }

    suspend fun addGame(game: Game) {
        gameDao.insertGame(game)
    }

    suspend fun updateGame(game: Game) {
        gameDao.updateGame(game)
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

    fun formatTimestamp(timestamp: Long?): String? {
        if (timestamp == null) return null
        return try {
            val date = Date(timestamp * 1000L)
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            format.format(date)
        } catch (e: Exception) {
            null
        }
    }
}
