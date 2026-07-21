package com.example.digitalcollectionmanager.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.digitalcollectionmanager.data.dao.GameDao
import com.example.digitalcollectionmanager.data.model.Game

@Database(entities = [Game::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
}