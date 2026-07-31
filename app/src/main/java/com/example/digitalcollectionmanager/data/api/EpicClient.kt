package com.example.digitalcollectionmanager.data.api

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
            header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        }
    }

    companion object {
        const val CLIENT_ID = "34a02cf8f4414e29b15d21ad766b651f"
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
    data class EpicLibraryResponse(
        val records: List<EpicLibraryRecord> = emptyList(),
        val cursor: String? = null
    )

    @Serializable
    data class EpicLibraryRecord(
        val catalogItemId: String,
        val appName: String? = null,
        val metadata: EpicMetadata? = null
    )

    @Serializable
    data class EpicMetadata(
        val displayName: String? = null
    )

    suspend fun exchangeCodeForToken(code: String): EpicTokenResponse? {
        return try {
            val auth = Base64.encodeToString("$CLIENT_ID:$CLIENT_SECRET".toByteArray(), Base64.NO_WRAP)
            val response = client.submitForm(
                url = "https://api.epicgames.dev/epic/oauth/v2/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", REDIRECT_URI)
                }
            ) {
                header("Authorization", "Basic $auth")
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

    suspend fun fetchLibraryItems(accessToken: String): List<EpicLibraryRecord> {
        val allRecords = mutableListOf<EpicLibraryRecord>()
        var cursor: String? = null

        try {
            do {
                val response = client.get("https://library-service.live.use1a.on.epicgames.com/library/api/public/items") {
                    header("Authorization", "Bearer $accessToken")
                    parameter("includeMetadata", "true")
                    if (cursor != null) parameter("cursor", cursor)
                }

                if (response.status == HttpStatusCode.OK) {
                    val body = response.body<EpicLibraryResponse>()
                    allRecords.addAll(body.records)
                    cursor = body.cursor
                } else {
                    println("Epic Library Error: ${response.status}")
                    break
                }
            } while (cursor != null)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return allRecords
    }
}
