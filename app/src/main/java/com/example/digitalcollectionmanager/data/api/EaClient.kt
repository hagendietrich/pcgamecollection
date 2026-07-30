package com.example.digitalcollectionmanager.data.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class EaClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
    }

    @Serializable
    data class EaIdentity(
        val pid: EaPid? = null
    )

    @Serializable
    data class EaPid(
        val pidId: Long? = null
    )

    @Serializable
    data class EaEntitlements(
        val entitlements: List<EaEntitlement> = emptyList()
    )

    @Serializable
    data class EaEntitlement(
        val offerId: String
    )

    @Serializable
    data class EaOfferResponse(
        val baseAttributes: EaBaseAttributes? = null,
        val localizableAttributes: EaLocalizableAttributes? = null
    )

    @Serializable
    data class EaBaseAttributes(
        val displayName: String? = null
    )

    @Serializable
    data class EaLocalizableAttributes(
        val displayName: String? = null
    )

    suspend fun fetchOwnedGames(remid: String, sid: String): List<String> {
        return try {
            val cookieHeader = "remid=$remid; sid=$sid"
            
            // Step 1: Get Persona ID (PID)
            val identity: JsonObject = client.get("https://gateway.ea.com/proxy/identity/pids/me") {
                header("Cookie", cookieHeader)
            }.body()
            
            val pidId = identity["pid"]?.jsonObject?.get("pidId")?.jsonPrimitive?.longOrNull ?: return emptyList()

            // Step 2: Get Entitlements
            val entitlementsResponse: JsonObject = client.get("https://gateway.ea.com/proxy/identity/pids/$pidId/entitlements?itemType=DEFAULT") {
                header("Cookie", cookieHeader)
            }.body()
            
            val entitlements = entitlementsResponse["entitlements"]?.jsonArray ?: return emptyList()
            entitlements.mapNotNull { it.jsonObject["offerId"]?.jsonPrimitive?.content }.distinct()
            
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchGameTitle(offerId: String): String? {
        return try {
            // This is a public API, no cookies needed
            val response: JsonObject = client.get("https://api1.origin.com/ecommerce2/public/$offerId/en_US") {
            }.body()
            
            response["localizableAttributes"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
                ?: response["baseAttributes"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
        } catch (e: Exception) {
            null
        }
    }
}
