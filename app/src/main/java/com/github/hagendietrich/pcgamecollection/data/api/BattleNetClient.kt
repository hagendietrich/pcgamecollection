package com.github.hagendietrich.pcgamecollection.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BattleNetClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            header("Origin", "https://account.battle.net")
            header("Referer", "https://account.battle.net/games")
        }
    }

    @Serializable
    data class BattleNetGame(
        @SerialName("titleId") val titleId: Long = 0,
        @SerialName("uid") val uid: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("localizedName") val localizedName: String? = null,
        @SerialName("localizedGameName") val localizedGameName: String? = null,
        @SerialName("gameName") val gameName: String? = null,
        @SerialName("gameTitle") val gameTitle: String? = null,
        @SerialName("gameAccountName") val gameAccountName: String? = null,
        @SerialName("productFamily") val productFamily: String? = null,
        @SerialName("classicGameType") val classicGameType: String? = null,
        @SerialName("customDownloadLink") val customDownloadLink: String? = null,
        @SerialName("isTrial") val isTrial: Boolean = false,
        @SerialName("playable") val playable: Boolean = true
    )

    private fun discoverGamesRecursive(element: JsonElement): List<BattleNetGame> {
        val results = mutableListOf<BattleNetGame>()
        when (element) {
            is JsonObject -> {
                // Check if this object has any name fields
                val nameFields = listOf("name", "localizedName", "localizedGameName", "gameName", "gameTitle", "gameAccountName")
                val hasName = element.keys.any { it in nameFields }
                
                if (hasName) {
                    try {
                        val game = json.decodeFromJsonElement<BattleNetGame>(element)
                        val finalName = game.gameName ?: game.gameTitle ?: game.localizedGameName ?: game.localizedName ?: game.name ?: game.gameAccountName
                        // Basic validation to avoid false positives (like region names or categories)
                        if (finalName != null && finalName.length > 2 && finalName != "Battle.net" && !finalName.contains("Account")) {
                            results.add(game)
                        } else if (game.customDownloadLink != null) {
                            // If it has a download link, it's definitely a game regardless of name validation
                            results.add(game)
                        }
                    } catch (e: Exception) {
                        // Not a matching game object
                    }
                }
                
                // Recurse into all children
                element.values.forEach { results.addAll(discoverGamesRecursive(it)) }
            }
            is JsonArray -> {
                element.forEach { results.addAll(discoverGamesRecursive(it)) }
            }
            else -> {}
        }
        return results
    }

    suspend fun fetchOwnedGames(cookies: String): List<BattleNetGame> {
        return try {
            val url = "https://account.battle.net/api/games-and-subs"
            val response = client.get(url) {
                header("Cookie", cookies)
            }

            if (response.status == HttpStatusCode.OK) {
                val rawBody = response.body<String>()
                val rootElement = json.parseToJsonElement(rawBody)

                // 1. Recursive discovery (Finds nested classic games)
                val allFound = discoverGamesRecursive(rootElement)
                
                // Detailed logging of discovery
                try {
                    val rootKeys = rootElement.jsonObject.keys.joinToString(", ")
                    println("Battle.net API Root Keys: [$rootKeys]")
                } catch (e: Exception) {}
                
                println("Battle.net API: Discovered ${allFound.size} raw items in JSON tree.")

                // 2. Filter and Deduplicate
                val filtered = allFound
                    .filter { !it.isTrial }
                    .distinctBy { it.titleId.takeIf { id -> id != 0L } ?: it.gameName ?: it.gameTitle ?: it.localizedGameName ?: it.localizedName ?: it.name ?: it.gameAccountName }
                    .sortedBy { it.gameName ?: it.gameTitle ?: it.localizedGameName ?: it.localizedName ?: it.name ?: it.gameAccountName }
                
                filtered.forEach { 
                    val n = it.gameName ?: it.gameTitle ?: it.localizedGameName ?: it.localizedName ?: it.name ?: it.gameAccountName
                    println("Battle.net API Item: TitleId=${it.titleId}, Name=$n, Type=${it.classicGameType ?: "Modern"}")
                }
                
                println("Battle.net API: ${filtered.size} unique items left after filtering.")
                filtered
            } else {
                val errorBody = response.body<String>()
                println("Battle.net API Error: ${response.status}. Body: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            println("Battle.net API Exception: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }
}
