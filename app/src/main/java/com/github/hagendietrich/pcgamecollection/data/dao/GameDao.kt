package com.github.hagendietrich.pcgamecollection.data.dao

import androidx.room.*
import com.github.hagendietrich.pcgamecollection.data.model.Game
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games ORDER BY dateAdded DESC")
    fun getAllGames(): Flow<List<Game>>

    @Query("SELECT * FROM games ORDER BY dateAdded DESC")
    fun getAllGamesSortedByDateAdded(): Flow<List<Game>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGame(game: Game)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGames(games: List<Game>)

    @Update
    suspend fun updateGame(game: Game)

    @Delete
    suspend fun deleteGame(game: Game)

    @Query("DELETE FROM games")
    suspend fun deleteAllGames()

    @Query("SELECT * FROM games WHERE id = :id")
    fun getGameByIdFlow(id: Int): Flow<Game?>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun getGameById(id: Int): Game?

    @Query("SELECT * FROM games WHERE title = :title LIMIT 1")
    suspend fun getGameByTitle(title: String): Game?

    @Query("SELECT * FROM games WHERE igdbId = :igdbId LIMIT 1")
    suspend fun getGameByIgdbId(igdbId: Long): Game?
}
