package com.example.digitalcollectionmanager.data.api

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

class UbisoftClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
            header("Ubi-AppId", APP_ID)
            header("Ubi-RequestedPlatformType", "uplay")
            header("Origin", "https://connect.ubisoft.com")
            header("Referer", "https://connect.ubisoft.com")
        }
    }

    companion object {
        const val APP_ID = "f35adcb5-1911-440c-b1c9-48fdc1701c68" // Verified Ubisoft AppId
        const val BASE_URL = "https://public-ubiservices.ubi.com"
        const val API_BASE_URL = "https://public-ubiservices.ubi.com"
    }

    @Serializable
    data class UbisoftGame(
        @SerialName("applicationId") val titleId: String,
        @SerialName("name") val name: String? = null,
        @SerialName("platform") val platform: String? = null,
        @SerialName("slug") val slug: String? = null,
        @SerialName("spaceId") val spaceId: String? = null
    )

    @Serializable
    data class UbisoftAggregationResponse(
        @SerialName("clubTitles") val clubTitles: List<UbisoftGame> = emptyList()
    )

    @Serializable
    data class UbisoftEntitlementsResponse(
        @SerialName("entitlements") val entitlements: List<UbisoftEntitlement> = emptyList()
    )

    @Serializable
    data class UbisoftEntitlement(
        @SerialName("productId") val productId: String,
        @SerialName("spaceId") val spaceId: String,
        @SerialName("type") val type: String
    )

    @Serializable
    data class UbisoftApplicationsResponse(
        @SerialName("applications") val applications: List<UbisoftApplicationMetadata> = emptyList()
    )

    @Serializable
    data class UbisoftApplicationMetadata(
        @SerialName("applicationId") val applicationId: String,
        @SerialName("name") val name: String,
        @SerialName("spaceId") val spaceId: String? = null
    )

    suspend fun fetchOwnedGames(ticket: String, sessionId: String): List<UbisoftGame> {
        // Strategy 1: Aggregation API (Best - returns names)
        val aggregationGames = tryFetchAggregation(ticket, sessionId)
        if (aggregationGames.isNotEmpty()) return aggregationGames

        println("Ubisoft Sync: Aggregation API failed, falling back to Entitlements API...")

        // Strategy 2: Entitlements API (Fallback - requires metadata call for names)
        return tryFetchEntitlementsWithMetadata(ticket, sessionId)
    }

    private suspend fun tryFetchAggregation(ticket: String, sessionId: String): List<UbisoftGame> {
        return try {
            val url = "$BASE_URL/v1/profiles/me/club/aggregation/website/games/owned"
            val response = client.get(url) {
                header("Authorization", "Ubi_v1 t=$ticket")
                header("Ubi-AppId", APP_ID)
                header("Ubi-SessionId", sessionId)
            }

            if (response.status == HttpStatusCode.OK) {
                val gamesResponse = response.body<UbisoftAggregationResponse>()
                gamesResponse.clubTitles.filter { it.name != null }
            } else {
                val errorBody = response.body<String>()
                println("Ubisoft Aggregation Error: ${response.status}, Body: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            println("Ubisoft Aggregation Exception: ${e.message}")
            emptyList()
        }
    }

    private suspend fun tryFetchEntitlementsWithMetadata(ticket: String, sessionId: String): List<UbisoftGame> {
        return try {
            // 1. Get flat list of product IDs
            val entUrl = "$API_BASE_URL/v1/profiles/me/global/ubiconnect/entitlement/api/entitlements"
            val entResponse = client.get(entUrl) {
                header("Authorization", "Ubi_v1 t=$ticket")
                header("Ubi-AppId", APP_ID)
                header("Ubi-SessionId", sessionId)
            }

            if (entResponse.status != HttpStatusCode.OK) {
                println("Ubisoft Entitlements Error: ${entResponse.status}")
                return emptyList()
            }

            val entitlements = entResponse.body<UbisoftEntitlementsResponse>().entitlements
            val gameEntitlements = entitlements.filter { it.type == "game" }
            if (gameEntitlements.isEmpty()) return emptyList()

            // 2. Fetch metadata (names) for these space IDs
            val spaceIds = gameEntitlements.map { it.spaceId }.distinct().joinToString(",")
            val metaUrl = "$API_BASE_URL/v2/applications"
            val metaResponse = client.get(metaUrl) {
                header("Authorization", "Ubi_v1 t=$ticket")
                header("Ubi-AppId", APP_ID)
                parameter("spaceIds", spaceIds)
            }

            if (metaResponse.status == HttpStatusCode.OK) {
                val metaData = metaResponse.body<UbisoftApplicationsResponse>().applications
                gameEntitlements.mapNotNull { ent ->
                    val meta = metaData.find { it.spaceId == ent.spaceId || it.applicationId == ent.productId }
                    if (meta != null) {
                        UbisoftGame(titleId = ent.productId, name = meta.name, spaceId = ent.spaceId)
                    } else null
                }
            } else {
                println("Ubisoft Metadata Error: ${metaResponse.status}")
                gameEntitlements.map { UbisoftGame(titleId = it.productId, name = "Unknown Ubisoft Game (${it.productId})") }
            }
        } catch (e: Exception) {
            println("Ubisoft Entitlements Fallback Exception: ${e.message}")
            emptyList()
        }
    }
}
