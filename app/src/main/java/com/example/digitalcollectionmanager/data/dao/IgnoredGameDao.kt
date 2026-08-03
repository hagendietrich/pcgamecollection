package com.example.digitalcollectionmanager.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.digitalcollectionmanager.data.model.IgnoredGame
import kotlinx.coroutines.flow.Flow

@Dao
interface IgnoredGameDao {
    @Query("SELECT * FROM ignored_games ORDER BY title ASC")
    fun getAllIgnoredGames(): Flow<List<IgnoredGame>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIgnoredGame(game: IgnoredGame)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIgnoredGames(games: List<IgnoredGame>)

    @Query("DELETE FROM ignored_games WHERE id = :id")
    suspend fun deleteIgnoredGame(id: Int)

    @Query("SELECT catalogItemId FROM ignored_games WHERE platform = :platform")
    suspend fun getIgnoredIdsByPlatform(platform: String): List<String>
}
