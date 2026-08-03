package com.example.digitalcollectionmanager.data.model

import kotlinx.serialization.Serializable

/**
 * Represents the root object for JSON export and import.
 * Contains the list of games and the list of ignored games.
 */
@Serializable
data class BackupData(
    val games: List<Game> = emptyList(),
    val ignoredGames: List<IgnoredGame> = emptyList(),
    val version: Int = 1
)
