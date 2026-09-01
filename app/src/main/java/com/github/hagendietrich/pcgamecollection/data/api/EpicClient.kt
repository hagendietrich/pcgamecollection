package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import android.util.Base64

// Safe extensions to avoid JsonNull crash
private val JsonElement?.safeObject: JsonObject? get() = this as? JsonObject
private val JsonElement?.safeArray: JsonArray? get() = this as? JsonArray

/** Current price of an Epic Games Store product in EUR. */
data class EpicPriceInfo(
    val currentPrice: Double,
    val originalPrice: Double?,
    val isOnSale: Boolean
)

class EpicClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        defaultRequest {
            header("User-Agent", "EpicGamesLauncher/15.21.0-26466986+++Portal+Release-Live Windows/10.0.19045.1.256.64bit")
        }
    }

    companion object {
        const val CLIENT_ID = "34a02cf8f4414e29b15921876da36f9a"
        const val CLIENT_SECRET = "daafbccc737745039dffe53d94fc76cf"
        const val REDIRECT_URI = "https://www.epicgames.com/id/api/redirect"
    }

    @Serializable
    data class EpicTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("token_type") val tokenType: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("account_id") val accountId: String? = null
    )

    @Serializable
    data class EpicLibraryRecord(
        val catalogItemId: String,
        val namespace: String? = null,
        val appName: String? = null,
        val sandboxName: String? = null,
        val recordType: String? = null
    )

    @Serializable
    data class EpicEntitlement(
        val id: String,
        val namespace: String,
        val catalogItemId: String,
        val title: String? = null,
        val entitlementType: String? = null,
        val grantDate: String? = null
    )

    @Serializable
    data class EpicMetadata(
        val displayName: String? = null
    )

    suspend fun exchangeCodeForToken(code: String): EpicTokenResponse? {
        return try {
            val auth = Base64.encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP)
            val response = client.submitForm(
                url = "https://account-public-service-prod03.ol.epicgames.com/account/api/oauth/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", REDIRECT_URI)
                }
            ) {
                header("Authorization", "Basic $auth")
                header("X-Epic-App", "epic-launcher")
            }

            if (response.status == HttpStatusCode.OK) {
                response.body<EpicTokenResponse>()
            } else {
                val errorBody = response.body<String>()
                println("Epic Token Error: ${response.status}, Body: $errorBody")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun fetchAccountId(accessToken: String): String? {
        return try {
            val response = client.get("https://account-public-service-prod03.ol.epicgames.com/account/api/public/account") {
                header("Authorization", "Bearer $accessToken")
                header("User-Agent", "EpicGamesLauncher/15.21.0-26466986+++Portal+Release-Live Windows/10.0.19045.1.256.64bit")
            }

            if (response.status == HttpStatusCode.OK) {
                val accountResponse = response.body<EpicAccountResponse>()
                println("Epic Sync: Fetched accountId: ${accountResponse.id}")
                accountResponse.id
            } else {
                val errorBody = response.body<String>()
                println("Epic Account ID Error: ${response.status}, Body: $errorBody")
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Serializable
    private data class EpicAccountResponse(
        val id: String,
        val displayName: String? = null,
        val email: String? = null
    )

    suspend fun fetchLibraryItems(accessToken: String): List<EpicLibraryRecord> {
        val allRecords = mutableListOf<EpicLibraryRecord>()
        var cursor: String? = null

        try {
            do {
                val response = client.get("https://library-service.live.use1a.on.epicgames.com/library/api/public/items") {
                    header("Authorization", "Bearer $accessToken")
                    header("User-Agent", "EpicGamesLauncher/15.21.0-26466986+++Portal+Release-Live Windows/10.0.19045.1.256.64bit")
                    parameter("includeMetadata", "true")
                    if (cursor != null) parameter("cursor", cursor)
                }

                if (response.status == HttpStatusCode.OK) {
                    val body = response.body<String>()
                    println("Epic API Raw Response: $body")
                    
                    // Parse the response manually to extract sandboxName and recordType
                    val json = Json { ignoreUnknownKeys = true }
                    val libraryResponse = json.decodeFromString<EpicLibraryResponse>(body)
                    allRecords.addAll(libraryResponse.records)
                    cursor = libraryResponse.cursor
                } else {
                    val errorBody = response.body<String>()
                    println("Epic Library Error: ${response.status}, Body: $errorBody")
                    break
                }
            } while (cursor != null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return allRecords
    }

    @Serializable
    data class EpicLibraryResponse(
        val records: List<EpicLibraryRecord> = emptyList(),
        val cursor: String? = null
    )

    @Serializable
    data class EpicAchievementDefinition(
        val name: String,
        val unlockedDisplayName: String? = null,
        val lockedDisplayName: String? = null,
        val unlockedDescription: String? = null,
        val lockedDescription: String? = null,
        val unlockedIconLink: String? = null,
        val isBase: Boolean = true
    )

    @Serializable
    data class EpicPlayerAchievement(
        val achievementName: String,
        val unlocked: Boolean = false,
        val unlockDate: String? = null
    )

    /**
     * Fetches the current price of an Epic Games Store product in EUR (German region).
     * Prices are returned in major units (e.g. 39.99).
     * Bypasses age gates by sending an age-verification cookie.
     */
    suspend fun fetchProductPrice(productSlug: String): EpicPriceInfo? {
        return try {
            val query = """
                query searchStoreQuery(${'$'}keywords: String, ${'$'}country: String!, ${'$'}locale: String!) {
                  Catalog {
                    searchStore(keywords: ${'$'}keywords, country: ${'$'}country, locale: ${'$'}locale) {
                      elements {
                        title
                        productSlug
                        price(country: ${'$'}country) {
                          totalPrice {
                            discountPrice
                            originalPrice
                            currencyCode
                          }
                        }
                      }
                    }
                  }
                }
            """.trimIndent()

            val response: JsonObject = client.post("https://store.epicgames.com/graphql") {
                contentType(ContentType.Application.Json)
                // Use browser-like UA for web store GraphQL to avoid Cloudflare blocks
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                // Bypass age gate for mature games
                header("Cookie", "HasAcceptedAgeGates=Generic%3A18")
                setBody(buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject {
                        put("keywords", productSlug)
                        put("country", "DE")
                        put("locale", "de")
                    })
                })
            }.body()

            val elements = response["data"].safeObject?.get("Catalog").safeObject
                ?.get("searchStore").safeObject?.get("elements").safeArray ?: return null

            // Find the element that matches our slug
            val element = elements.mapNotNull { it.safeObject }.find { 
                it["productSlug"]?.jsonPrimitive?.content == productSlug 
            } ?: elements.firstOrNull().safeObject ?: return null

            val totalPrice = element["price"].safeObject?.get("totalPrice").safeObject ?: return null
            val discountPriceCents = totalPrice["discountPrice"]?.jsonPrimitive?.intOrNull ?: 0
            val originalPriceCents = totalPrice["originalPrice"]?.jsonPrimitive?.intOrNull ?: discountPriceCents

            val current = discountPriceCents / 100.0
            val original = originalPriceCents / 100.0

            EpicPriceInfo(
                currentPrice = current,
                originalPrice = if (current < original) original else null,
                isOnSale = current < original
            )
        } catch (e: Exception) {
            println("Epic Price Error for slug $productSlug: ${e.message}")
            null
        }
    }

    suspend fun getSandboxIdFromSlug(productSlug: String, gameTitle: String? = null): String? {
        val url = "https://store.epicgames.com/graphql"
        Log.d("EpicClient", "Resolving SandboxID for '$productSlug' (Title: $gameTitle) from $url")
        
        // 1. Try mapping Catalog ID -> Namespace directly if it's a hex ID
        if (productSlug.matches(Regex("^[a-f0-9]{32}$"))) {
            val mappingQuery = """
                query catalogItem(${'$'}id: String!) {
                  Catalog {
                    catalogItem(id: ${'$'}id) {
                      namespace
                    }
                  }
                }
            """.trimIndent()
            
            try {
                val response: JsonObject = client.post(url) {
                    contentType(ContentType.Application.Json)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setBody(buildJsonObject {
                        put("query", mappingQuery)
                        put("variables", buildJsonObject { put("id", productSlug) })
                    })
                }.body()
                
                val namespace = response["data"].safeObject?.get("Catalog").safeObject
                    ?.get("catalogItem").safeObject?.get("namespace")?.jsonPrimitive?.content
                
                if (!namespace.isNullOrBlank()) {
                    Log.d("EpicClient", "Resolved SandboxID from Catalog ID lookup: $namespace")
                    return namespace
                }
            } catch (e: Exception) {
                Log.w("EpicClient", "Catalog mapping lookup failed: ${e.message}")
            }
        }

        // 2. Search store by keywords (Slug or ID)
        val searchStoreQuery = """
            query searchStoreQuery(${'$'}keywords: String, ${'$'}country: String!) {
              Catalog {
                searchStore(keywords: ${'$'}keywords, country: ${'$'}country) {
                  elements {
                    namespace
                    productSlug
                    title
                  }
                }
              }
            }
        """.trimIndent()

        suspend fun performSearch(keywords: String): JsonArray? {
            return try {
                val response: JsonObject = client.post(url) {
                    contentType(ContentType.Application.Json)
                    header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setBody(buildJsonObject {
                        put("query", searchStoreQuery)
                        put("variables", buildJsonObject {
                            put("keywords", keywords)
                            put("country", "DE")
                        })
                    })
                }.body()
                response["data"].safeObject?.get("Catalog").safeObject
                    ?.get("searchStore").safeObject?.get("elements").safeArray
            } catch (e: Exception) {
                null
            }
        }

        var elements = performSearch(productSlug)
        
        // 3. Fallback: Search by Game Title if ID/Slug search failed
        if ((elements == null || elements.isEmpty()) && !gameTitle.isNullOrBlank()) {
            Log.d("EpicClient", "Searching by title fallback: $gameTitle")
            elements = performSearch(gameTitle)
        }

        if (elements == null || elements.isEmpty()) {
            Log.d("EpicClient", "No elements found in store search.")
            return if (productSlug.matches(Regex("^[a-f0-9]{32}$"))) productSlug else null
        }

        // 4. Match search results
        val list = elements.mapNotNull { it.safeObject }
        
        // Try exact slug match
        val elementBySlug = list.find { it["productSlug"]?.jsonPrimitive?.content == productSlug }
        
        // Try exact namespace match (if input was a sandbox ID)
        val elementByNamespace = list.find { it["namespace"]?.jsonPrimitive?.content == productSlug }
        
        // Try title match
        val elementByTitle = gameTitle?.let { title ->
            list.find { it["title"]?.jsonPrimitive?.content?.equals(title, ignoreCase = true) == true }
        }

        val bestElement = elementBySlug ?: elementByNamespace ?: elementByTitle ?: list.firstOrNull()

        val sandboxId = bestElement?.get("namespace")?.jsonPrimitive?.content
        Log.d("EpicClient", "Resolved SandboxID: $sandboxId")
        return sandboxId ?: if (productSlug.matches(Regex("^[a-f0-9]{32}$"))) productSlug else null
    }

    suspend fun fetchAchievementSchema(sandboxId: String): List<EpicAchievementDefinition> {
        val url = "https://store.epicgames.com/graphql"
        Log.d("EpicClient", "Fetching achievement schema for Sandbox $sandboxId from $url")
        val query = """
            query Achievement(${'$'}sandboxId: String!, ${'$'}locale: String!) {
              Achievement {
                productAchievementsRecordBySandbox(sandboxId: ${'$'}sandboxId, locale: ${'$'}locale) {
                  achievements {
                    achievement {
                      name
                      unlockedDisplayName
                      lockedDisplayName
                      unlockedDescription
                      lockedDescription
                      unlockedIconLink
                      isBase
                    }
                  }
                }
              }
            }
        """.trimIndent()

        return try {
            val response: JsonObject = client.post(url) {
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setBody(buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject {
                        put("sandboxId", sandboxId)
                        put("locale", "en-US")
                    })
                })
            }.body()

            val record = response["data"].safeObject?.get("Achievement").safeObject
                ?.get("productAchievementsRecordBySandbox").safeObject ?: return emptyList()
            
            val achievementsArray = record["achievements"].safeArray ?: return emptyList()
            Log.d("EpicClient", "Epic schema response: found ${achievementsArray.size} achievements")
            
            achievementsArray.mapNotNull { element ->
                val ach = element.safeObject?.get("achievement").safeObject ?: return@mapNotNull null
                Json { ignoreUnknownKeys = true }.decodeFromJsonElement<EpicAchievementDefinition>(ach)
            }
        } catch (e: Exception) {
            Log.e("EpicClient", "Error fetching Epic achievement schema: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun fetchUserAchievements(accessToken: String, accountId: String, sandboxId: String): List<EpicPlayerAchievement> {
        val query = """
            query PlayerGameAchievementProgress(${'$'}epicAccountId: String!, ${'$'}sandboxId: String!, ${'$'}locale: String!) {
              PlayerAchievement {
                playerAchievementGameRecordsBySandbox(
                  epicAccountId: ${'$'}epicAccountId
                  sandboxId: ${'$'}sandboxId
                  locale: ${'$'}locale
                ) {
                  records {
                    achievements {
                      achievementName
                      unlocked
                      unlockDate
                    }
                  }
                }
              }
            }
        """.trimIndent()

        return try {
            val response: JsonObject = client.post("https://store.epicgames.com/graphql") {
                header("Authorization", "Bearer $accessToken")
                contentType(ContentType.Application.Json)
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setBody(buildJsonObject {
                    put("query", query)
                    put("variables", buildJsonObject {
                        put("epicAccountId", accountId)
                        put("sandboxId", sandboxId)
                        put("locale", "en-US")
                    })
                })
            }.body()

            val playerAch = response["data"].safeObject?.get("PlayerAchievement").safeObject
                ?.get("playerAchievementGameRecordsBySandbox").safeObject ?: return emptyList()
            
            val records = playerAch["records"].safeArray ?: return emptyList()
            val firstRecord = records.firstOrNull().safeObject ?: return emptyList()
            
            val achievementsArray = firstRecord["achievements"].safeArray ?: return emptyList()
            
            achievementsArray.mapNotNull { element ->
                val ach = element.safeObject ?: return@mapNotNull null
                Json { ignoreUnknownKeys = true }.decodeFromJsonElement<EpicPlayerAchievement>(ach)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
