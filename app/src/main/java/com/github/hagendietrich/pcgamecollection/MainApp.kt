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

        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `parentIgdbId` INTEGER")
                db.execSQL("ALTER TABLE `wishlist_games` ADD COLUMN `parentIgdbId` INTEGER")
            }
        }

        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `category` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `wishlist_games` ADD COLUMN `category` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix nullability for 'category' in 'games'
                db.execSQL("CREATE TABLE IF NOT EXISTS `games_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `platforms` TEXT NOT NULL, `coverImageUrl` TEXT, `releaseDate` TEXT, `isOwned` INTEGER NOT NULL, `igdbId` INTEGER, `sourceIds` TEXT NOT NULL, `playtimes` TEXT NOT NULL, `playtimeMinutes` INTEGER NOT NULL, `genres` TEXT NOT NULL, `labels` TEXT NOT NULL, `completionStatus` TEXT NOT NULL, `isReleaseDateManual` INTEGER NOT NULL, `isGenreManual` INTEGER NOT NULL, `isGameModeManual` INTEGER NOT NULL, `isCoverManual` INTEGER NOT NULL, `summary` TEXT, `screenshotUrls` TEXT NOT NULL, `igdbUrl` TEXT, `userRating` REAL, `criticRating` REAL, `developers` TEXT NOT NULL, `publishers` TEXT NOT NULL, `themes` TEXT NOT NULL, `keywords` TEXT NOT NULL, `franchises` TEXT NOT NULL, `series` TEXT NOT NULL, `gameModes` TEXT NOT NULL, `storeUrls` TEXT NOT NULL, `personalRating` INTEGER, `parentIgdbId` INTEGER, `category` INTEGER, `dateAdded` INTEGER NOT NULL)")
                
                // Explicitly list all columns and handle potential missing/null columns like 'storeUrls'
                db.execSQL("INSERT INTO `games_new` (id, title, platforms, coverImageUrl, releaseDate, isOwned, igdbId, sourceIds, playtimes, playtimeMinutes, genres, labels, completionStatus, isReleaseDateManual, isGenreManual, isGameModeManual, isCoverManual, summary, screenshotUrls, igdbUrl, userRating, criticRating, developers, publishers, themes, keywords, franchises, series, gameModes, storeUrls, personalRating, parentIgdbId, category, dateAdded) " +
                           "SELECT id, title, platforms, coverImageUrl, releaseDate, isOwned, igdbId, sourceIds, playtimes, playtimeMinutes, genres, labels, completionStatus, isReleaseDateManual, isGenreManual, isGameModeManual, isCoverManual, summary, screenshotUrls, igdbUrl, userRating, criticRating, developers, publishers, themes, keywords, franchises, series, gameModes, COALESCE(storeUrls, '{}'), personalRating, parentIgdbId, category, dateAdded FROM `games` ")
                db.execSQL("DROP TABLE `games` ")
                db.execSQL("ALTER TABLE `games_new` RENAME TO `games` ")

                // Fix nullability for 'category' in 'wishlist_games'
                db.execSQL("CREATE TABLE IF NOT EXISTS `wishlist_games_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, `coverImageUrl` TEXT, `igdbId` INTEGER, `parentIgdbId` INTEGER, `category` INTEGER, `platformPrices` TEXT NOT NULL, `dateAdded` INTEGER NOT NULL, `notes` TEXT)")
                db.execSQL("INSERT INTO `wishlist_games_new` (id, title, coverImageUrl, igdbId, parentIgdbId, category, platformPrices, dateAdded, notes) " +
                           "SELECT id, title, coverImageUrl, igdbId, parentIgdbId, category, platformPrices, dateAdded, notes FROM `wishlist_games` ")
                db.execSQL("DROP TABLE `wishlist_games` ")
                db.execSQL("ALTER TABLE `wishlist_games_new` RENAME TO `wishlist_games` ")
            }
        }

        private val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `hltbMain` INTEGER")
                db.execSQL("ALTER TABLE `games` ADD COLUMN `hltbMainExtra` INTEGER")
                db.execSQL("ALTER TABLE `games` ADD COLUMN `hltbCompletionist` INTEGER")
                db.execSQL("ALTER TABLE `wishlist_games` ADD COLUMN `hltbMain` INTEGER")
                db.execSQL("ALTER TABLE `wishlist_games` ADD COLUMN `hltbMainExtra` INTEGER")
                db.execSQL("ALTER TABLE `wishlist_games` ADD COLUMN `hltbCompletionist` INTEGER")
            }
        }

        private val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `playtimeSource` TEXT")
                db.execSQL("ALTER TABLE `wishlist_games` ADD COLUMN `playtimeSource` TEXT")
            }
        }

        private val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `notes` TEXT")
            }
        }

        private val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `games` ADD COLUMN `achievements` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE `games` ADD COLUMN `achievementsSource` TEXT")
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
        .addMigrations(MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26) // preserve library data on upgrade to v26
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
