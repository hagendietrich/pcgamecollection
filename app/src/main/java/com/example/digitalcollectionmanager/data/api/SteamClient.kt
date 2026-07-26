package com.example.digitalcollectionmanager.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SteamClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    @Serializable
    data class SteamOwnedGamesResponse(
        val response: SteamGamesList? = null
    )

    @Serializable
    data class SteamGamesList(
        val game_count: Int = 0,
        val games: List<SteamGame> = emptyList()
    )

    @Serializable
    data class SteamGame(
        val appid: Int,
        val name: String,
        val playtime_forever: Int = 0 // Minutes
    )

    @Serializable
    data class SteamVanityResponse(
        val response: SteamVanityResult? = null
    )

    @Serializable
    data class SteamVanityResult(
        val steamid: String? = null,
        val success: Int = 0
    )

    suspend fun resolveVanityUrl(apiKey: String, input: String): String? {
        // If input is already a 17-digit numeric SteamID, return it
        if (input.length == 17 && input.all { it.isDigit() }) {
            return input
        }

        // Clean input: remove URL parts if the user pasted a full link
        val vanityName = input.trim()
            .removeSuffix("/")
            .substringAfterLast("/")
            .substringAfterLast("=")

        return try {
            val response: SteamVanityResponse = client.get("https://api.steampowered.com/ISteamUser/ResolveVanityURL/v1/") {
                parameter("key", apiKey)
                parameter("vanityurl", vanityName)
            }.body()
            
            if (response.response?.success == 1) {
                response.response.steamid
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchOwnedGames(apiKey: String, steamId: String): List<SteamGame> {
        return try {
            val response: SteamOwnedGamesResponse = client.get("https://api.steampowered.com/IPlayerService/GetOwnedGames/v1/") {
                parameter("key", apiKey)
                parameter("steamid", steamId)
                parameter("include_appinfo", "true")
                parameter("format", "json")
            }.body()
            
            response.response?.games ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
