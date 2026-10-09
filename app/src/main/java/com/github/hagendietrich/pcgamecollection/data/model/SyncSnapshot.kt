package com.github.hagendietrich.pcgamecollection.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a device snapshot export file stored in the shared sync directory.
 * Named: pcgc_sync_<deviceId>.json
 */
@Serializable
data class SyncSnapshot(
    val deviceId: String,
    val deviceName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val games: List<Game> = emptyList(),
    val ignoredGames: List<IgnoredGame> = emptyList(),
    val wishlistGames: List<WishlistGame> = emptyList(),
    val deletedRecords: List<DeletedGame> = emptyList(),
    val version: Int = 1
)
