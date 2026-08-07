package com.github.hagendietrich.pcgamecollection.data.model

/**
 * Represents the origin of a game entry in the collection.
 * This allows the app to distinguish between manually added games
 * and those imported from external services like Steam or GOG.
 */
enum class GameSource {
    MANUAL,
    IGDB,
    STEAM,
    GOG,
    UBISOFT,
    EA,
    EPIC,
    BATTLE_NET
}
