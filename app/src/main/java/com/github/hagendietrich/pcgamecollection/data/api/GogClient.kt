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

/** A product from GOG's public catalog search. */
data class GogSearchProduct(
    val productId: String,
    val slug: String,
    val title: String
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
     * Searches GOG's public catalog for products matching the given query.
     * Used as a fallback when IGDB has no GOG external_games entry (common for
     * brand-new releases whose IGDB store data lags behind).
     */
    suspend fun searchProduct(query: String): List<GogSearchProduct> {
        return try {
            val response: JsonObject = client.get("https://catalog.gog.com/v1/catalog") {
                parameter("query", "like:$query")
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "application/json")
            }.body()

            val products = response["products"]?.jsonArray ?: return emptyList()
            products.mapNotNull { element ->
                runCatching {
                    val obj = element.jsonObject
                    // catalog v1 returns ID as a string, but handle both for safety
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@runCatching null
                    val slug = obj["slug"]?.jsonPrimitive?.content ?: return@runCatching null
                    val title = obj["title"]?.jsonPrimitive?.content ?: return@runCatching null
                    GogSearchProduct(productId = id, slug = slug, title = title)
                }.getOrNull()
            }
        } catch (e: Exception) {
            println("GOG Search Error for '$query': ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetches the current price of a GOG product in EUR (German region by default).
     * Prices are returned in major units (e.g. 14.99).
     * Bypasses age gates (HTML redirects) by sending an age-verification cookie.
     * Returns null if the product has no price or the fetch fails.
     */
    suspend fun fetchProductPrice(productId: String, countryCode: String = "DE"): GogPriceInfo? {
        return try {
            val currency = when (countryCode.uppercase()) {
                "US" -> "USD"
                "GB" -> "GBP"
                "PL" -> "PLN"
                else -> "EUR"
            }
            val gogLc = "${countryCode.uppercase()}_${currency}_en-US"

            val response: String = client.get("https://api.gog.com/products/$productId/prices") {
                parameter("countryCode", countryCode)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept", "application/json")
                // Bypass age gate for mature games
                header("Cookie", "age_check=1; gog_lc=$gogLc")
            }.body()

            // Detect age gate redirect: GOG returns HTML with age confirmation instead of JSON
            if (response.trim().startsWith("<") || !response.trim().startsWith("{")) {
                if (countryCode == "DE") {
                    println("GOG Price Warning for Product $productId: Age gate still detected for DE. Trying fallback region AT...")
                    return fetchProductPrice(productId, "AT")
                }
                println("GOG Price Warning for Product $productId: Age gate detected (HTML response) for $countryCode")
                return null
            }

            val json = Json.decodeFromString<JsonObject>(response)
            val pricesArray = json["_embedded"]?.jsonObject?.get("prices")?.jsonArray ?: return null
            val first = pricesArray.firstOrNull()?.jsonObject ?: return null

            // GOG API can return either 'amount' (double) or 'finalPrice' (string in cents like "199 EUR")
            fun parseGogPrice(priceElement: JsonElement?): Double? {
                val content = priceElement?.jsonPrimitive?.content ?: return null
                // Try direct double first
                content.toDoubleOrNull()?.let { return it }
                // Extract digits and treat as cents: "199 EUR" -> 1.99
                val digits = content.filter { it.isDigit() }.toDoubleOrNull() ?: return null
                return digits / 100.0
            }

            val base = first["amount"]?.jsonPrimitive?.doubleOrNull ?: parseGogPrice(first["basePrice"])
            val finalAmount = first["finalAmount"]?.jsonPrimitive?.doubleOrNull ?: parseGogPrice(first["finalPrice"])
            
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
