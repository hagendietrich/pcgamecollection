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
    val completionStatus: CompletionStatus = CompletionStatus.BACKLOG
)
