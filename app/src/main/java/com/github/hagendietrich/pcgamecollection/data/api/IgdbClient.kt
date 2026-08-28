package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
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

    suspend fun authenticate(clientId: String, clientSecret: String): Boolean = try {
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

    suspend fun searchGames(clientId: String, query: String): List<IgdbGame> {
        val token = accessToken ?: return emptyList()
        
        return try {
            val response = client.post("https://api.igdb.com/v4/games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("search \"$query\"; fields name, game_type, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name, parent_game, dlcs, expansions, bundles, standalone_expansions; limit 20;")
            }
            val bodyString = response.body<String>()
            
            return Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString<List<IgdbGame>>(bodyString)
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
            val response = client.post("https://api.igdb.com/v4/games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                // Use explicit field list to get nested data like cover.url and genres.name
                setBody("fields name, game_type, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name, parent_game, dlcs, expansions, bundles, standalone_expansions; where id = ($idsString); limit 500;")
            }
            val bodyString = response.body<String>()
            Log.d("IgdbClient", "IGDB Full Response for IDs $idsString: $bodyString")
            Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString<List<IgdbGame>>(bodyString)
        } catch (e: Exception) {
            Log.e("IgdbClient", "IGDB GetByIds Error: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Finds the main game that contains this bundle.
     * Category 3 is Bundle in IGDB.
     */
    suspend fun getParentForBundle(clientId: String, bundleId: Long): IgdbGame? {
        val token = accessToken ?: return null
        
        return try {
            // First, find a game that has this bundle in its bundles list (a constituent DLC/expansion)
            val constituentGames: List<IgdbGame> = client.post("https://api.igdb.com/v4/games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("fields id, parent_game; where bundles = ($bundleId); limit 1;")
            }.body()
            
            val constituentGame = constituentGames.firstOrNull()
            val parentId = constituentGame?.parentGame
            
            // If we found a parent ID, fetch the full game object for it
            return if (parentId != null) {
                val parentGames: List<IgdbGame> = client.post("https://api.igdb.com/v4/games") {
                    header("Client-ID", clientId)
                    header("Authorization", "Bearer $token")
                    setBody("fields id, name, game_type, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name, parent_game, dlcs, expansions, bundles, standalone_expansions; where id = ($parentId); limit 1;")
                }.body()
                parentGames.firstOrNull()
            } else null
        } catch (e: Exception) {
            println("IGDB GetParentForBundle Error: ${e.message}")
            null
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