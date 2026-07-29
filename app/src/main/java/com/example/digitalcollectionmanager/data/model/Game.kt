package com.example.digitalcollectionmanager.data.model

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
    val summary: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val igdbUrl: String? = null,
    val userRating: Double? = null,
    val criticRating: Double? = null,
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val themes: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val gameModes: List<String> = emptyList(),
    val storeUrls: Map<String, String> = emptyMap()
)
