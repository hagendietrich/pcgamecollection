package com.github.hagendietrich.pcgamecollection.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.github.hagendietrich.pcgamecollection.data.dao.GameDao
import com.github.hagendietrich.pcgamecollection.data.dao.IgnoredGameDao
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.model.IgnoredGame

@Database(entities = [Game::class, IgnoredGame::class], version = 15, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun ignoredGameDao(): IgnoredGameDao
}
