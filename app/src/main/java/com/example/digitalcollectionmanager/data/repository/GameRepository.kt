package com.example.digitalcollectionmanager.data.repository

import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.dao.GameDao
import com.example.digitalcollectionmanager.data.model.Game
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class GameRepository(
    private val gameDao: GameDao,
    private val igdbClient: IgdbClient,
    private val settingsRepository: SettingsRepository
) {

    /**
     * Searches for games on IGDB.
     * Fetches credentials from SettingsRepository and authenticates if necessary.
     */
    suspend fun searchGames(query: String): List<IgdbGame> {
        val clientId = settingsRepository.clientId.firstOrNull() ?: return emptyList()
        val clientSecret = settingsRepository.clientSecret.firstOrNull() ?: return emptyList()

        // Authenticate with IGDB before searching
        val authSuccess = igdbClient.authenticate(clientId, clientSecret)
        if (!authSuccess) return emptyList()

        return igdbClient.searchGames(clientId, query)
    }

    /**
     * Adds a game to the local Room database.
     */
    suspend fun addGame(game: Game) {
        gameDao.insertGame(game)
    }

    /**
     * Deletes a game from the local Room database.
     */
    suspend fun deleteGame(game: Game) {
        gameDao.deleteGame(game)
    }

    /**
     * Returns a Flow of all games in the collection for real-time UI updates.
     */
    fun getAllGames(): Flow<List<Game>> {
        return gameDao.getAllGames()
    }

    /**
     * Checks if a game with the given title already exists in the local collection.
     */
    suspend fun getGameByTitle(title: String): Game? {
        return gameDao.getGameByTitle(title)
    }

    /**
     * Checks if a game with the given external ID already exists.
     */
    suspend fun getGameByExternalId(externalId: Long, source: String): Game? {
        return gameDao.getGameByExternalId(externalId, source)
    }

    /**
     * Helper to get the high-quality cover URL from an IGDB thumbnail URL.
     */
    fun getFullCoverUrl(thumbUrl: String?): String? {
        return thumbUrl?.let { igdbClient.getFullCoverUrl(it) }
    }
}
