package com.github.hagendietrich.pcgamecollection.data.api.models

import kotlinx.serialization.Serializable

/**
 * Represents a mapping between an IGDB game and an external service (Steam, GOG, etc.)
 */
@Serializable
data class IgdbExternalGame(
    val id: Long,
    val category: Int, // e.g., 1 for Steam, 5 for GOG
    val uid: String,   // The ID on the external service (e.g., Steam AppID)
    val game: Long? = null // The IGDB ID this maps to
)

/**
 * Common External Game Categories for IGDB API v4
 */
object IgdbExternalCategory {
    const val STEAM = 1
    const val GOG = 5
    const val YOUTUBE = 10
    const val MICROSOFT_STORE = 11
    const val APPLE_APP_STORE = 13
    const val TWITCH = 14
    const val GOOGLE_PLAY = 15
    const val AMAZON = 20
    const val EPIC_GAMES = 26
    const val OCULUS = 28
    const val ITCH_IO = 30
    const val XBOX_MARKETPLACE = 31
    const val UBISOFT_CONNECT = 34
    const val ORIGIN = 35
    const val PLAYSTATION_STORE_US = 36
}
