package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
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

    @Serializable
    data class GogAchievementSchema(
        val api_key: String? = null, // In products expand it might be 'achievement_id' or 'api_key'
        val achievement_id: String? = null,
        val name: String,
        val description: String? = null,
        val unlocked_icon_url: String? = null,
        val locked_icon_url: String? = null,
        val visible_before_unlocking: Boolean = true
    ) {
        val bestKey: String get() = api_key ?: achievement_id ?: ""
    }

    @Serializable
    data class GogProductResponse(
        val achievements: List<GogAchievementSchema> = emptyList()
    )

    @Serializable
    data class GogPlayerAchievementsResponse(
        val items: List<GogPlayerAchievement> = emptyList()
    )

    @Serializable
    data class GogPlayerAchievement(
        val achievement_key: String,
        val unlocked: Boolean = false,
        val unlock_date: String? = null
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

    suspend fun resolveUserId(username: String): String? {
        val url = "https://www.gog.com/u/$username"
        Log.d("GogClient", "Resolving UserID for $username from $url")
        return try {
            val response: String = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            }.body()
            
            // Try Pattern 1: profileUser object in gogData
            val pattern1 = Regex("\"profileUser\"\\s*:\\s*\\{[^}]*\"id\"\\s*:\\s*\"(\\d+)\"")
            // Try Pattern 2: direct userId field
            val pattern2 = Regex("\"userId\"\\s*:\\s*\"(\\d+)\"")
            // Try Pattern 3: user ID in avatar URL
            val pattern3 = Regex("/user/(\\d+)/")

            val userId = pattern1.find(response)?.groupValues?.get(1)
                ?: pattern2.find(response)?.groupValues?.get(1)
                ?: pattern3.find(response)?.groupValues?.get(1)
            
            Log.d("GogClient", "Resolved UserID: $userId")
            userId
        } catch (e: Exception) {
            Log.e("GogClient", "Error resolving UserID: ${e.message}")
            null
        }
    }

    suspend fun fetchAchievementSchema(productId: String): List<GogAchievementSchema> {
        val url = "https://api.gog.com/products/$productId"
        Log.d("GogClient", "Fetching achievement metadata for Product $productId from $url")
        return try {
            val response = client.get(url)
            if (response.status == HttpStatusCode.OK) {
                val responseBody = response.bodyAsText()
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val product = json.decodeFromString<JsonObject>(responseBody)
                
                // Try to find achievements in the product JSON
                val achs = product["achievements"]?.jsonArray
                if (achs != null) {
                    Log.d("GogClient", "GOG schema response: found ${achs.size} achievements")
                    return json.decodeFromString<List<GogAchievementSchema>>(achs.toString())
                }
            }
            Log.d("GogClient", "Product API did not contain achievements list, status: ${response.status}")
            emptyList()
        } catch (e: Exception) {
            Log.e("GogClient", "Error fetching GOG achievement schema: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchUserAchievements(productId: String, userId: String, username: String? = null): List<GogPlayerAchievement> {
        val url = "https://gameplay.gog.com/clients/$productId/users/$userId/achievements"
        Log.d("GogClient", "Fetching user achievements for Product $productId, User $userId from $url")
        return try {
            val response = client.get(url)
            if (response.status == HttpStatusCode.OK) {
                val playerResponse: GogPlayerAchievementsResponse = response.body()
                Log.d("GogClient", "GOG user achievements response: found ${playerResponse.items.size} entries")
                playerResponse.items
            } else {
                val errorBody = response.bodyAsText()
                Log.e("GogClient", "Error fetching GOG user achievements: Status ${response.status}, Body: $errorBody")
                
                // Fallback: Scrape public profile if Unauthorized or Not Found
                if (username != null && (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden || response.status == HttpStatusCode.NotFound)) {
                    return fetchUserAchievementsScraped(productId, username, userId)
                }
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("GogClient", "Error fetching GOG user achievements: ${e.message}")
            // Catch-all fallback
            if (username != null) fetchUserAchievementsScraped(productId, username, userId) else emptyList()
        }
    }

    /**
     * Fallback method that scrapes the user's public profile for achievement status.
     * Only works if the profile is set to "Public".
     */
    private suspend fun fetchUserAchievementsScraped(productId: String, username: String, userId: String? = null): List<GogPlayerAchievement> {
        val url = if (userId != null) {
            "https://www.gog.com/u/$username/game/$productId?sort_user_id=$userId&sort=user_unlock_date"
        } else {
            "https://www.gog.com/u/$username/game/$productId"
        }
        Log.d("GogClient", "Attempting to scrape public profile for achievements: $url")
        
        return try {
            val response: String = client.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                header("Accept-Language", "en-US,en;q=0.9")
            }.bodyAsText()
            
            val achievements = mutableListOf<GogPlayerAchievement>()
            
            // Look for the global profilesData object
            val profilesDataRegex = Regex("window\\.profilesData\\.achievements\\s*=\\s*(.*?);")
            val match = profilesDataRegex.find(response)
            
            if (match != null) {
                val jsonStr = match.groupValues[1]
                Log.d("GogClient", "Found profilesData.achievements JSON blob")
                
                val json = Json { ignoreUnknownKeys = true }
                val achsArray = json.parseToJsonElement(jsonStr).jsonArray
                
                achsArray.forEach { achElement ->
                    val entry = achElement.jsonObject
                    val ach = entry["achievement"]?.jsonObject ?: return@forEach
                    
                    val key = ach["achievement_id"]?.jsonPrimitive?.content 
                        ?: ach["api_key"]?.jsonPrimitive?.content
                        ?: ach["id"]?.jsonPrimitive?.content
                    
                    // The stats object is keyed by UserID
                    val stats = entry["stats"]?.jsonObject
                    var isUnlocked = false
                    
                    if (stats != null) {
                        // Check if ANY user in stats has unlocked it (usually only one user present)
                        for (uId in stats.keys) {
                            val userStats = stats[uId]?.jsonObject
                            if (userStats?.get("isUnlocked")?.jsonPrimitive?.boolean == true ||
                                userStats?.get("is_unlocked")?.jsonPrimitive?.boolean == true) {
                                isUnlocked = true
                                break
                            }
                        }
                    }
                    
                    if (key != null) {
                        achievements.add(GogPlayerAchievement(key, isUnlocked))
                    }
                }
            } else {
                Log.w("GogClient", "Could not find window.profilesData.achievements in HTML")
                
                // Fallback to __NEXT_DATA__
                val nextDataRegex = Regex("<script id=\"__NEXT_DATA__\"[^>]*>(.*?)</script>")
                val nextDataMatch = nextDataRegex.find(response)
                
                if (nextDataMatch != null) {
                    val jsonStr = nextDataMatch.groupValues[1]
                    val jsonObj = Json { ignoreUnknownKeys = true }.parseToJsonElement(jsonStr).jsonObject
                    
                    fun findAchievements(element: JsonElement?): JsonArray? {
                        if (element == null) return null
                        if (element is JsonObject) {
                            element["achievements"]?.jsonArray?.let { return it }
                            for (k in element.keys) {
                                findAchievements(element[k])?.let { return it }
                            }
                        } else if (element is JsonArray) {
                            for (item in element) {
                                findAchievements(item)?.let { return it }
                            }
                        }
                        return null
                    }

                    val achs = findAchievements(jsonObj)
                    achs?.forEach { achElement ->
                        val ach = achElement.jsonObject
                        val key = ach["achievement_id"]?.jsonPrimitive?.content 
                            ?: ach["api_key"]?.jsonPrimitive?.content
                        val unlocked = ach["is_unlocked"]?.jsonPrimitive?.boolean 
                            ?: ach["unlocked"]?.jsonPrimitive?.boolean 
                            ?: false
                        
                        if (key != null) {
                            achievements.add(GogPlayerAchievement(key, unlocked))
                        }
                    }
                }
            }

            Log.d("GogClient", "Scraped ${achievements.size} achievement statuses from public profile")
            achievements
        } catch (e: Exception) {
            Log.e("GogClient", "Error scraping GOG achievements: ${e.message}")
            emptyList()
        }
    }
}
