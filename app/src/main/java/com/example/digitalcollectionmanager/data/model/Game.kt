package com.example.digitalcollectionmanager.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class Game(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val platform: String, // e.g., "Steam", "GOG"
    val coverImageUrl: String?,
    val releaseDate: String? = null,
    val isOwned: Boolean = true,
    val externalId: Long? = null, // ID from IGDB, Steam, etc.
    val source: String = "MANUAL", // Origin: IGDB, STEAM, etc.
    val genres: List<String> = emptyList(), // List of genres from source
    val playtimeMinutes: Int = 0,
    val labels: List<String> = emptyList(),
    val completionStatus: CompletionStatus = CompletionStatus.BACKLOG
)
