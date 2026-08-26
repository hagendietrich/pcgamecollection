package com.github.hagendietrich.pcgamecollection.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

/** Current price of a GOG product in EUR. */
data class GogPriceInfo(
    val currentPrice: Double,
    val originalPrice: Double?,
    val isOnSale: Boolean
)

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

    /**
     * Fetches the current price of a GOG product in EUR (German region).
     * Prices are returned in major units (e.g. 14.99).
     * Returns null if the product has no price or the fetch fails.
     */
    suspend fun fetchProductPrice(productId: String): GogPriceInfo? {
        return try {
            val response: JsonObject = client.get("https://api.gog.com/products/$productId/prices") {
                parameter("countryCode", "DE")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "application/json")
            }.body()

            val pricesArray = response["_embedded"]?.jsonObject?.get("prices")?.jsonArray ?: return null
            val first = pricesArray.firstOrNull()?.jsonObject ?: return null

            val base = first["amount"]?.jsonPrimitive?.doubleOrNull
            val finalAmount = first["finalAmount"]?.jsonPrimitive?.doubleOrNull
            if (base == null && finalAmount == null) return null

            val original = base ?: finalAmount!!
            val current = finalAmount ?: base!!

            GogPriceInfo(
                currentPrice = current,
                originalPrice = if (current < original) original else null,
                isOnSale = current < original
            )
        } catch (e: Exception) {
            println("GOG Price Error for Product $productId: ${e.message}")
            null
        }
    }

    suspend fun fetchPublicGames(username: String): List<GogGame> {
        val allGames = mutableListOf<GogGame>()
        var currentPage = 1
        var totalPagesCount = 1

        try {
            do {
                if (currentPage > 1) delay(200)

                val url = "https://www.gog.com/u/$username/games/stats?page=$currentPage"
                println("GOG Sync: Fetching $url")
                
                val response: JsonObject = client.get(url) {
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    header("Accept", "application/hal+json")
                }.body()

                response["pages"]?.jsonPrimitive?.intOrNull?.let { 
                    totalPagesCount = it
                }
                
                val totalGames = response["total"]?.jsonPrimitive?.intOrNull ?: 0
                
                val embedded = response["_embedded"]?.jsonObject
                val items = embedded?.get("items")?.jsonArray ?: run {
                    println("GOG Sync Warning: No items array found on page $currentPage. Raw Response: $response")
                    null
                } ?: break
                
                val pageGames = items.mapNotNull { itemElement ->
                    try {
                        val item = itemElement.jsonObject
                        val gameInfo = item["game"]?.jsonObject
                        val title = gameInfo?.get("title")?.jsonPrimitive?.content ?: return@mapNotNull null
                        val gogId = gameInfo["id"]?.jsonPrimitive?.content ?: ""
                        
                        // Handle stats being either an object or an empty array
                        val statsElement = item["stats"]
                        var playtime = 0
                        if (statsElement is JsonObject) {
                            statsElement.values.firstOrNull()?.jsonObject?.let { userStats ->
                                playtime = userStats["playtime"]?.jsonPrimitive?.int ?: 0
                            }
                        }

                        GogGame(title, playtime, gogId)
                    } catch (e: Exception) {
                        println("GOG Sync: Error parsing game in items: ${e.message}")
                        null
                    }
                }
                
                if (pageGames.isNotEmpty()) {
                    allGames.addAll(pageGames)
                    println("GOG Sync: Page $currentPage finished. Subtotal: ${allGames.size}/$totalGames")
                } else {
                    println("GOG Sync: No games found on page $currentPage. Full response: $response")
                }

                currentPage++
                if (currentPage > 50) break 
                
            } while (currentPage <= totalPagesCount)

        } catch (e: Exception) {
            println("GOG Sync Critical Error: ${e.message}")
            e.printStackTrace()
        }
        println("GOG Sync: Finished. Total games collected: ${allGames.size}")
        return allGames
    }
}
