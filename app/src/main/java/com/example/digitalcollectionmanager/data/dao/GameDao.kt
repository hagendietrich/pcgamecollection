package com.example.digitalcollectionmanager.data.dao

import androidx.room.*
import com.example.digitalcollectionmanager.data.model.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games")
    fun getAllGames(): Flow<List<Game>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: Game)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<Game>)

    @Update
    suspend fun updateGame(game: Game)

    @Delete
    suspend fun deleteGame(game: Game)

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: Int): Game?

    @Query("SELECT * FROM games WHERE title = :title LIMIT 1")
    suspend fun getGameByTitle(title: String): Game?

    @Query("SELECT * FROM games WHERE externalId = :externalId AND source = :source LIMIT 1")
    suspend fun getGameByExternalId(externalId: Long, source: String): Game?
}
