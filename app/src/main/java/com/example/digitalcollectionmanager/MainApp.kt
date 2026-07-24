package com.example.digitalcollectionmanager

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.digitalcollectionmanager.data.database.AppDatabase

/**
 * Application class that lazily creates a single Room database instance for the whole app.
 * The database is created when the application starts and stored as a property that can
 * be accessed by ViewModels or any other component via `App.instance().database`.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class App : Application() {
    companion object {
        private var _instance: App? = null

        /** Returns the application instance. Should be called only from an Activity/Service context. */
        @Synchronized
        fun instance(): App { return _instance ?: throw IllegalStateException("App not initialized yet") }
    }

    lateinit var database: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        _instance = this
        // Create a persistent Room DB – we use the official name of the app from resources.
        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "digital_collection.db"
        ).fallbackToDestructiveMigration() // no migration implemented yet; safe for dev
        .build()
    }

    /** Helper that returns a fresh in‑memory instance of the database – useful for unit tests */
    fun createInMemoryDatabase(): AppDatabase {
        return Room.inMemoryDatabaseBuilder(
            applicationContext,
            AppDatabase::class.java
        ).build()
    }
}
