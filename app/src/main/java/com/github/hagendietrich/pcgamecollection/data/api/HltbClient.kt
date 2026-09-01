package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class CodepotatoesGame(
    val title: String? = null,
    val mainStory: Double = 0.0,
    @SerialName("mainStoryWithExtras") val mainStoryWithExtras: Double? = null,
    val completionist: Double = 0.0
) {
    /** Returns the best available total time for Main + Extra content. */
    fun getBestExtra(): Double {
        return mainStoryWithExtras ?: mainStory
    }
}

class HltbClient(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }
) {
    private val baseUrl = "https://hltbapi.codepotatoes.de"

    suspend fun getGameBySteamId(appId: String): CodepotatoesGame? {
        Log.d("HltbClient", "Fetching HLTB by Steam ID: $appId")
        return try {
            val response = client.get("$baseUrl/steam/$appId")
            if (response.status == HttpStatusCode.OK) {
                Log.d("HltbClient", "Steam ID lookup success for $appId")
                response.body<CodepotatoesGame>()
            } else {
                Log.w("HltbClient", "Steam ID $appId not found: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("HltbClient", "Steam ID lookup error for $appId: ${e.message}")
            null
        }
    }

    suspend fun getGameByGogId(gogId: String): CodepotatoesGame? {
        // Endpoint not confirmed to work; kept as optional fallback.
        Log.d("HltbClient", "Attempting GOG lookup for $gogId (may not be available)")
        return try {
            val response = client.get("$baseUrl/gog/$gogId")
            if (response.status == HttpStatusCode.OK) {
                response.body<CodepotatoesGame>()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("HltbClient", "GOG lookup failed for $gogId, falling back to IGDB")
            null
        }
    }

    suspend fun getGameByHltbId(hltbId: String): CodepotatoesGame? {
        Log.d("HltbClient", "HLTB ID lookup not implemented (removed search fallback)")
        return null
    }

    /** Converts hours (Double) to minutes (Int). */
    fun toMinutes(hours: Double): Int = (hours * 60).toInt()
}
