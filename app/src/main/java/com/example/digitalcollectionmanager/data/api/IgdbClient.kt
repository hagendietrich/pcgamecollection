package com.example.digitalcollectionmanager.data.api

import com.example.digitalcollectionmanager.data.api.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class IgdbClient {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }

    private var accessToken: String? = null

    suspend fun authenticate(clientId: String, clientSecret: String): Boolean {
        return try {
            val response: IgdbTokenResponse = client.post("https://id.twitch.tv/oauth2/token") {
                parameter("client_id", clientId)
                parameter("client_secret", clientSecret)
                parameter("grant_type", "client_credentials")
            }.body()
            
            accessToken = response.accessToken
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun searchGames(clientId: String, query: String): List<IgdbGame> {
        val token = accessToken ?: return emptyList()
        
        return try {
            client.post("https://api.igdb.com/v4/games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("search \"$query\"; fields name, first_release_date, cover.url, genres.name; limit 20;")
            }.body()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getFullCoverUrl(thumbUrl: String): String {
        // IGDB urls usually look like //images.igdb.com/igdb/image/upload/t_thumb/co1r8v.jpg
        // We want to change t_thumb to t_cover_big
        return "https:" + thumbUrl.replace("t_thumb", "t_cover_big")
    }
}
