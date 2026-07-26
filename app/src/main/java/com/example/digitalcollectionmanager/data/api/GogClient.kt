package com.example.digitalcollectionmanager.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

class GogClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    data class GogGame(
        val title: String,
        val playtimeMinutes: Int = 0,
        val gogId: String
    )

    suspend fun fetchPublicGames(username: String): List<GogGame> {
        val allGames = mutableListOf<GogGame>()
        var currentPage = 1
        var totalPages = 1

        try {
            do {
                if (currentPage > 1) delay(200) // Small delay to prevent rate-limiting

                val url = "https://www.gog.com/u/$username/games/stats?page=$currentPage"
                println("GOG Sync: Fetching $url")
                
                val response: JsonObject = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    header("Accept", "application/hal+json")
                }.body()

                // Extract pagination info
                totalPages = response["pages"]?.jsonPrimitive?.int ?: 1
                val totalGames = response["total_items"]?.jsonPrimitive?.int ?: 0
                println("GOG Sync: Page $currentPage of $totalPages (Total games reported by GOG: $totalGames)")
                
                val embedded = response["_embedded"]?.jsonObject
                val items = embedded?.get("items")?.jsonArray ?: break
                
                val pageGames = items.mapNotNull { itemElement ->
                    try {
                        val item = itemElement.jsonObject
                        val gameInfo = item["game"]?.jsonObject
                        val title = gameInfo?.get("title")?.jsonPrimitive?.content ?: return@mapNotNull null
                        val gogId = gameInfo["id"]?.jsonPrimitive?.content ?: ""
                        
                        val stats = item["stats"]?.jsonObject
                        var playtime = 0
                        stats?.values?.firstOrNull()?.jsonObject?.let { userStats ->
                            playtime = userStats["playtime"]?.jsonPrimitive?.int ?: 0
                        }

                        GogGame(title, playtime, gogId)
                    } catch (e: Exception) {
                        null
                    }
                }
                println("GOG Sync: Successfully parsed ${pageGames.size} games from page $currentPage")
                allGames.addAll(pageGames)
                
                // If we got 0 games but are supposed to have more pages, something is wrong
                if (pageGames.isEmpty() && currentPage < totalPages) {
                    println("GOG Sync Warning: Empty page encountered at $currentPage/$totalPages")
                    break 
                }

                currentPage++
                
                if (currentPage > 50) break // Safety break
                
            } while (currentPage <= totalPages)

        } catch (e: Exception) {
            println("GOG Sync Critical Error: ${e.message}")
            e.printStackTrace()
        }
        println("GOG Sync: Finished. Total games collected: ${allGames.size}")
        return allGames
    }
}
