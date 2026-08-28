package com.github.hagendietrich.pcgamecollection.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.github.hagendietrich.pcgamecollection.data.model.WishlistGame
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistGameDao {
    @Query("SELECT * FROM wishlist_games ORDER BY dateAdded DESC")
    fun getAllWishlistGames(): Flow<List<WishlistGame>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlistGame(game: WishlistGame)

    @Update
    suspend fun updateWishlistGame(game: WishlistGame)

    @Delete
    suspend fun deleteWishlistGame(game: WishlistGame)

    @Query("DELETE FROM wishlist_games")
    suspend fun deleteAllWishlistGames()

    @Query("SELECT * FROM wishlist_games WHERE id = :id")
    suspend fun getWishlistGameById(id: Int): WishlistGame?

    @Query("SELECT * FROM wishlist_games WHERE igdbId = :igdbId LIMIT 1")
    suspend fun getWishlistGameByIgdbId(igdbId: Long): WishlistGame?

    @Query("SELECT * FROM wishlist_games WHERE parentIgdbId = :parentIgdbId")
    fun getWishlistDlcForGame(parentIgdbId: Long): Flow<List<WishlistGame>>
}
