package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.*

/** Current price of a Steam app in EUR. */
data class SteamPriceInfo(
    val currentPrice: Double,
    val originalPrice: Double?,
    val isOnSale: Boolean
)

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

    @Serializable
    data class SteamAchievementSchemaResponse(
        val game: SteamGameSchema? = null
    )

    @Serializable
    data class SteamGameSchema(
        val availableGameStats: SteamAvailableStats? = null
    )

    @Serializable
    data class SteamAvailableStats(
        val achievements: List<SteamAchievementDefinition> = emptyList()
    )

    @Serializable
    data class SteamAchievementDefinition(
        val name: String,
        val displayName: String? = null,
        val description: String? = null,
        val icon: String? = null,
        val icongray: String? = null,
        val hidden: Int = 0
    )

    @Serializable
    data class SteamPlayerAchievementsResponse(
        val playerstats: SteamPlayerStats? = null
    )

    @Serializable
    data class SteamPlayerStats(
        val achievements: List<SteamPlayerAchievement> = emptyList(),
        val error: String? = null,
        val success: JsonElement? = null // Can be Boolean or Int
    ) {
        val isSuccessful: Boolean get() = when (val s = success) {
            is JsonPrimitive -> {
                if (s.isString) s.content.lowercase() == "true"
                else s.booleanOrNull == true || s.intOrNull == 1
            }
            else -> false
        }
    }

    @Serializable
    data class SteamPlayerAchievement(
        val apiname: String,
        val achieved: Int = 0,
        val unlocktime: Long = 0
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

    /**
     * Fetches the current price of a Steam app in EUR (German region).
     * Prices are returned in major units (e.g. 14.99).
     * Returns null if no price is available (e.g. free-to-play or fetch failure).
     */
    suspend fun fetchCurrentPrice(appId: String): SteamPriceInfo? {
        return try {
            val response: JsonObject = client.get("https://store.steampowered.com/api/appdetails") {
                parameter("appids", appId)
                parameter("cc", "de")
                parameter("filters", "price_overview")
            }.body()

            val appData = response[appId]?.jsonObject ?: return null
            if (appData["success"]?.jsonPrimitive?.booleanOrNull != true) return null

            val overview = appData["data"]?.jsonObject?.get("price_overview")?.jsonObject ?: return null
            val finalCents = overview["final"]?.jsonPrimitive?.intOrNull ?: return null
            val initialCents = overview["initial"]?.jsonPrimitive?.intOrNull ?: finalCents
            val discountPercent = overview["discount_percent"]?.jsonPrimitive?.intOrNull ?: 0

            SteamPriceInfo(
                currentPrice = finalCents / 100.0,
                originalPrice = if (finalCents < initialCents) initialCents / 100.0 else null,
                isOnSale = discountPercent > 0 && finalCents < initialCents
            )
        } catch (e: Exception) {
            println("Steam Price Error for AppID $appId: ${e.message}")
            null
        }
    }

    suspend fun fetchAchievementSchema(apiKey: String, appId: Int): List<SteamAchievementDefinition> {
        val url = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/"
        Log.d("SteamClient", "Fetching achievement schema for AppID $appId from $url")
        return try {
            val response = client.get(url) {
                parameter("key", apiKey)
                parameter("appid", appId)
                parameter("l", "en") // Ensure English descriptions
            }
            
            if (response.status == HttpStatusCode.OK) {
                val schemaResponse: SteamAchievementSchemaResponse = response.body()
                val count = schemaResponse.game?.availableGameStats?.achievements?.size ?: 0
                Log.d("SteamClient", "Steam schema response: found $count achievements")
                schemaResponse.game?.availableGameStats?.achievements ?: emptyList()
            } else {
                val errorBody = response.bodyAsText()
                Log.e("SteamClient", "Error fetching achievement schema: Status ${response.status}, Body: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SteamClient", "Error fetching achievement schema: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchUserAchievements(apiKey: String, steamId: String, appId: Int): List<SteamPlayerAchievement> {
        // Try GetPlayerAchievements first
        var achievements = fetchUserAchievementsInternal(
            "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/",
            apiKey, steamId, appId
        )
        
        // Fallback to GetUserStatsForGame if empty or failed
        if (achievements.isEmpty()) {
            Log.d("SteamClient", "GetPlayerAchievements empty, trying GetUserStatsForGame fallback...")
            achievements = fetchUserAchievementsInternal(
                "https://api.steampowered.com/ISteamUserStats/GetUserStatsForGame/v2/",
                apiKey, steamId, appId
            )
        }

        // Tier 3 Fallback: Legacy XML API (often bypasses JSON API privacy quirks)
        if (achievements.isEmpty()) {
            Log.d("SteamClient", "JSON APIs failed, trying Legacy XML fallback...")
            achievements = fetchUserAchievementsXml(steamId, appId)
        }
        
        return achievements
    }

    private suspend fun fetchUserAchievementsXml(steamId: String, appId: Int): List<SteamPlayerAchievement> {
        val url = "https://steamcommunity.com/profiles/$steamId/stats/$appId/?xml=1"
        Log.d("SteamClient", "Fetching user achievements from XML: $url")
        return try {
            val response = client.get(url).bodyAsText()
            
            val results = mutableListOf<SteamPlayerAchievement>()
            // Very simple XML parsing for <achievement> blocks
            val achRegex = Regex("<achievement>.*?</achievement>", RegexOption.DOT_MATCHES_ALL)
            val apiNameRegex = Regex("<apiname>(.*?)</apiname>")
            val closedRegex = Regex("<closed>(\\d)</closed>") // 1 = Unlocked
            val unlockTimeRegex = Regex("<unlockTime>(\\d+)</unlockTime>")

            achRegex.findAll(response).forEach { match ->
                val content = match.value
                val apiName = apiNameRegex.find(content)?.groupValues?.get(1)
                val isUnlocked = closedRegex.find(content)?.groupValues?.get(1) == "1"
                val unlockTime = unlockTimeRegex.find(content)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                if (apiName != null) {
                    results.add(SteamPlayerAchievement(
                        apiname = apiName,
                        achieved = if (isUnlocked) 1 else 0,
                        unlocktime = unlockTime
                    ))
                }
            }
            Log.d("SteamClient", "XML Fallback: Found ${results.size} achievements (${results.count { it.achieved == 1 }} unlocked)")
            results
        } catch (e: Exception) {
            Log.e("SteamClient", "XML Fallback failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchUserAchievementsInternal(url: String, apiKey: String, steamId: String, appId: Int): List<SteamPlayerAchievement> {
        Log.d("SteamClient", "Fetching user achievements from $url (AppID: $appId, User: $steamId)")
        return try {
            val response = client.get(url) {
                parameter("key", apiKey)
                parameter("steamid", steamId)
                parameter("appid", appId)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                
                // Both APIs return almost the same structure under playerstats
                val stats = json.decodeFromString<SteamPlayerAchievementsResponse>(body).playerstats
                
                if (stats?.isSuccessful == true) {
                    val count = stats.achievements.size
                    Log.d("SteamClient", "Steam user achievements response ($url): success, found $count entries")
                    stats.achievements
                } else {
                    Log.w("SteamClient", "Steam user achievements response ($url): failed (success=${stats?.success}, error=${stats?.error})")
                    emptyList()
                }
            } else {
                val errorBody = response.bodyAsText()
                Log.e("SteamClient", "Error fetching user achievements from $url: Status ${response.status}, Body: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("SteamClient", "Error fetching user achievements from $url: ${e.message}")
            emptyList()
        }
    }
}
