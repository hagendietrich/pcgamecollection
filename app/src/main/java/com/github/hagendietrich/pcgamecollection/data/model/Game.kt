package com.github.hagendietrich.pcgamecollection.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val platforms: List<String> = emptyList(), // e.g., ["Steam", "GOG"]
    val coverImageUrl: String?,
    val releaseDate: String? = null,
    val isOwned: Boolean = true,
    val igdbId: Long? = null, // Master ID for deduplication (from IGDB)
    val sourceIds: Map<String, String> = emptyMap(), // e.g., {"STEAM": "400", "GOG": "1234"}
    val playtimes: Map<String, Int> = emptyMap(), // Playtime per source in minutes
    val playtimeMinutes: Int = 0, // Aggregated total playtime in minutes
    val genres: List<String> = emptyList(),
    val labels: List<String> = emptyList(),
    val completionStatus: CompletionStatus = CompletionStatus.BACKLOG,
    val isReleaseDateManual: Boolean = false,
    val isGenreManual: Boolean = false,
    val isGameModeManual: Boolean = false,
    val isCoverManual: Boolean = false,
    val summary: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val igdbUrl: String? = null,
    val userRating: Double? = null,
    val criticRating: Double? = null,
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val franchises: List<String> = emptyList(),
    val series: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val storeUrls: Map<String, String> = emptyMap(),
    val personalRating: Int? = null,
    val parentIgdbId: Long? = null,
    val category: Int? = null, // 0 = Main Game, 1 = DLC, 2 = Expansion, 3 = Bundle, etc.
    val hltbMain: Int? = null, // Main story playtime in minutes
    val hltbMainExtra: Int? = null, // Main + Extra playtime in minutes
    val hltbCompletionist: Int? = null, // Completionist playtime in minutes
    val playtimeSource: String? = null, // e.g., "HLTB", "IGDB"
    val notes: String? = null,
    val achievements: List<Achievement> = emptyList(),
    val achievementsSource: String? = null, // "Steam", "GOG", "Epic"
    val dateAdded: Long = System.currentTimeMillis(), // Timestamp when the game was added to the database
    val bundleIgdbIds: List<Long> = emptyList()
)

fun Game.getCategoryDisplay(): String =
    when (category) {
        0, 8, 11 -> "Main Game"
        1, 6, 7, 14 -> "DLC"
        2, 4, 5 -> "Expansion"
        3, 13 -> "Bundle"
        9, 10 -> "Remaster"
        else -> "Unknown"
    }