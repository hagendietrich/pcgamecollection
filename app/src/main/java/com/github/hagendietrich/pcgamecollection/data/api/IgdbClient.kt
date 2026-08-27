package com.github.hagendietrich.pcgamecollection.data.api

import com.github.hagendietrich.pcgamecollection.data.api.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class IgdbClient(
    private val client: HttpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
    }
) {
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
                setBody("search \"$query\"; fields name, category, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name; limit 20;")
            }.body()
        } catch (e: Exception) {
            println("IGDB Search Error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun resolveExternalGames(clientId: String, category: Int, uids: List<String>): List<IgdbExternalGame> {
        val token = accessToken ?: return emptyList()
        if (uids.isEmpty()) return emptyList()

        val uidsString = uids.joinToString(",") { "\"$it\"" }
        return try {
            client.post("https://api.igdb.com/v4/external_games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("fields game, uid, category; where category = $category & uid = ($uidsString); limit 500;")
            }.body()
        } catch (e: Exception) {
            println("IGDB Resolve External Error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getGamesByIds(clientId: String, ids: List<Long>): List<IgdbGame> {
        val token = accessToken ?: return emptyList()
        if (ids.isEmpty()) return emptyList()

        val idsString = ids.joinToString(",")
        return try {
            client.post("https://api.igdb.com/v4/games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("fields name, category, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name; where id = ($idsString); limit 500;")
            }.body()
        } catch (e: Exception) {
            println("IGDB GetByIds Error: ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetches ALL external game entries (store links) for a given IGDB game directly from the
     * external_games endpoint. This is required because nested `external_games` inside game
     * search results are truncated by the IGDB API and often miss store entries like Steam.
     */
    suspend fun getExternalGamesForGame(clientId: String, gameId: Long): List<IgdbExternalGameData> {
        val token = accessToken ?: return emptyList()

        return try {
            val externals: List<IgdbExternalGameData> = client.post("https://api.igdb.com/v4/external_games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("fields game, category, uid, url; where game = ($gameId); limit 200;")
            }.body()
            println("IGDB ExternalGames: game $gameId -> ${externals.size} entries")
            externals
        } catch (e: Exception) {
            println("IGDB ExternalGamesForGame Error: ${e.message}")
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
