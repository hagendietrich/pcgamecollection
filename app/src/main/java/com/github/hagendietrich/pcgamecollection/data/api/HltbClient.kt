package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class CodepotatoesGame(
    val title: String? = null,
    val mainStory: Double = 0.0,
    val mainPlusExtra: Double = 0.0,
    @SerialName("mainStoryWithExtras") val mainStoryWithExtras: Double? = null,
    @SerialName("main_plus_extra") val mainPlusExtraSnake: Double? = null,
    @SerialName("mainPlusSides") val mainPlusSides: Double? = null,
    @SerialName("mainSides") val mainSides: Double? = null,
    @SerialName("gameplayMainExtra") val gameplayMainExtra: Double? = null,
    @SerialName("gameplayMainSides") val gameplayMainSides: Double? = null,
    val mainExtra: Double = 0.0,
    val completionist: Double = 0.0
) {
    /** Returns the best available total time for Main + Extra content. */
    fun getBestExtra(): Double {
        // Prioritize specific fields that often contain the correct sum
        // 115.73 vs 72.7 -> mainStoryWithExtras is clearly the winner
        val candidates = listOfNotNull(mainStoryWithExtras, mainPlusExtra, mainPlusExtraSnake, mainPlusSides, mainSides, gameplayMainExtra, gameplayMainSides)
        
        // Find the highest value among candidates that is greater than the main story
        val best = candidates.filter { it > (mainStory + 0.1) }.maxOrNull()
        
        return if (best != null) {
            best
        } else {
            // Fallback: If no sum field is larger, try calculating it manually
            if (mainExtra > 0.1) mainStory + mainExtra else mainStory
        }
    }
}

class HltbClient(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }
) {
    private val baseUrl = "https://hltbapi.codepotatoes.de"

    suspend fun getGameBySteamId(appId: String): CodepotatoesGame? {
        Log.d("HltbClient", "Fetching by Steam ID: $baseUrl/steam/$appId")
        return try {
            val response = client.get("$baseUrl/steam/$appId")
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                Log.d("HltbClient", "Raw Steam Response: $body")
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                json.decodeFromString<CodepotatoesGame>(body)
            } else {
                Log.d("HltbClient", "Steam ID lookup failed with status: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("HltbClient", "Steam ID lookup error: ${e.message}")
            null
        }
    }

    suspend fun getGameByGogId(gogId: String): CodepotatoesGame? {
        Log.d("HltbClient", "Fetching by GOG ID: $baseUrl/v1/gog/$gogId")
        return try {
            val response = client.get("$baseUrl/v1/gog/$gogId")
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                Log.d("HltbClient", "Raw GOG Response: $body")
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                json.decodeFromString<CodepotatoesGame>(body)
            } else {
                Log.d("HltbClient", "GOG ID lookup failed with status: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("HltbClient", "GOG ID lookup error: ${e.message}")
            null
        }
    }

    suspend fun getGameByHltbId(hltbId: String): CodepotatoesGame? {
        Log.d("HltbClient", "Fetching by HLTB ID: $baseUrl/v1/game/$hltbId")
        return try {
            val response = client.get("$baseUrl/v1/game/$hltbId")
            if (response.status == HttpStatusCode.OK) {
                val body = response.bodyAsText()
                Log.d("HltbClient", "Raw HLTB ID Response: $body")
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                json.decodeFromString<CodepotatoesGame>(body)
            } else {
                Log.d("HltbClient", "HLTB ID lookup failed with status: ${response.status}")
                null
            }
        } catch (e: Exception) {
            Log.e("HltbClient", "HLTB ID lookup error: ${e.message}")
            null
        }
    }

    suspend fun searchGame(title: String): CodepotatoesGame? {
        // Try multiple versioned and unversioned endpoints with different body formats and trailing slashes
        val attempts = listOf(
            Triple("$baseUrl/v1/query/", "POST", buildJsonObject { put("searchTerms", buildJsonArray { add(title) }); put("searchPage", 1); put("size", 20) }),
            Triple("$baseUrl/v1/search/", "POST", buildJsonObject { put("search", title) }),
            Triple("$baseUrl/v1/search/", "GET", buildJsonObject { }), // URL params handled below
            Triple("$baseUrl/search/", "POST", buildJsonObject { put("searchTerms", buildJsonArray { add(title) }); put("searchPage", 1); put("size", 20) }),
            Triple("$baseUrl/v1/query", "POST", buildJsonObject { put("searchTerms", buildJsonArray { add(title) }); put("searchPage", 1); put("size", 20) })
        )

        for ((url, method, body) in attempts) {
            Log.d("HltbClient", "Searching HLTB: $url ($method) with body $body")
            try {
                val response = if (method == "POST") {
                    client.post(url) {
                        contentType(ContentType.Application.Json)
                        setBody(body)
                    }
                } else {
                    client.get(url) {
                        parameter("search", title)
                        parameter("title", title)
                    }
                }

                if (response.status == HttpStatusCode.OK) {
                    val responseBody = response.bodyAsText()
                    Log.d("HltbClient", "Success from $url: $responseBody")
                    val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                    
                    val results = try {
                        json.decodeFromString<List<CodepotatoesGame>>(responseBody)
                    } catch (_: Exception) {
                        listOf(json.decodeFromString<CodepotatoesGame>(responseBody))
                    }

                    if (results.isNotEmpty()) {
                        val match = results.find { it.title?.equals(title, ignoreCase = true) == true }
                            ?: results.find { it.title?.startsWith(title, ignoreCase = true) == true }
                            ?: results.first()
                        
                        Log.d("HltbClient", "Matched: ${match.title}")
                        return match
                    }
                } else {
                    Log.d("HltbClient", "Attempt failed for $url: ${response.status}")
                }
            } catch (e: Exception) {
                Log.w("HltbClient", "Error during attempt for $url: ${e.message}")
            }
        }

        // Try base title if applicable
        if (title.contains(":")) {
            val shortTitle = title.split(":")[0].trim()
            Log.d("HltbClient", "Retrying with base title: '$shortTitle'")
            return searchGame(shortTitle)
        }
        if (title.contains("(") && title.contains(")")) {
            val cleanedTitle = title.replace(Regex("\\(.*?\\)"), "").trim()
            if (cleanedTitle != title) {
                Log.d("HltbClient", "Retrying with cleaned title: '$cleanedTitle'")
                return searchGame(cleanedTitle)
            }
        }
        
        return null
    }

    /** Converts hours (Double) to minutes (Int). */
    fun toMinutes(hours: Double): Int = (hours * 60).toInt()
}
