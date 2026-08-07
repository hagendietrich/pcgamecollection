package com.github.hagendietrich.pcgamecollection.data.api

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
import kotlinx.serialization.json.Json
import android.util.Base64

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
}
