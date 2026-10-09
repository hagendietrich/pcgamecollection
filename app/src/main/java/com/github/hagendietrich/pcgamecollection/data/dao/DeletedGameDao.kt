package com.github.hagendietrich.pcgamecollection.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.hagendietrich.pcgamecollection.data.model.DeletedGame
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedGameDao {
    @Query("SELECT * FROM deleted_games")
    suspend fun getAllDeletedGamesOnce(): List<DeletedGame>

    @Query("SELECT * FROM deleted_games")
    fun getAllDeletedGames(): Flow<List<DeletedGame>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedGame(deletedGame: DeletedGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeletedGames(deletedGames: List<DeletedGame>)

    @Query("DELETE FROM deleted_games")
    suspend fun clearAll()
}
