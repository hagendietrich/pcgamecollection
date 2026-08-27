package com.github.hagendietrich.pcgamecollection

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.github.hagendietrich.pcgamecollection.data.database.AppDatabase

/**
 * Application class that lazily creates a single Room database instance for the whole app.
 * The database is created when the application starts and stored as a property that can
 * be accessed by ViewModels or any other component via `App.instance().database`.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class App : Application(), ImageLoaderFactory {
    companion object {
        private var _instance: App? = null

        /** Returns the application instance. Should be called only from an Activity/Service context. */
        @Synchronized
        fun instance(): App { return _instance ?: throw IllegalStateException("App not initialized yet") }

        /**
         * v15 -> v16: adds the wishlist_games table (Wishlist feature).
         * Column order/types must exactly match Room's generated schema for WishlistGame:
         * (id, title, coverImageUrl, igdbId, platformPrices, dateAdded, notes)
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `wishlist_games` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`title` TEXT NOT NULL, " +
                            "`coverImageUrl` TEXT, " +
                            "`igdbId` INTEGER, " +
                            "`platformPrices` TEXT NOT NULL, " +
                            "`dateAdded` INTEGER NOT NULL, " +
                            "`notes` TEXT)"
                )
            }
        }

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `personalRating` INTEGER")
            }
        }

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `franchises` TEXT NOT NULL DEFAULT '[]'")
            }
        }

        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `series` TEXT NOT NULL DEFAULT '[]'")
            }
        }
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
        )
        .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19) // preserve library data on upgrade to v19
        .fallbackToDestructiveMigration() // safety net for pre-15 versions
        .build()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
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
