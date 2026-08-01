package com.example.digitalcollectionmanager.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.digitalcollectionmanager.data.dao.GameDao
import com.example.digitalcollectionmanager.data.dao.IgnoredGameDao
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.IgnoredGame

@Database(entities = [Game::class, IgnoredGame::class], version = 15, exportSchema = true)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun ignoredGameDao(): IgnoredGameDao
}
