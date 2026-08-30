package com.github.hagendietrich.pcgamecollection.data.api

import android.util.Log
import com.github.hagendietrich.pcgamecollection.data.api.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
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
                setBody("search \"$query\"; fields id, name, game_type, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name, parent_game, dlcs, expansions, bundles, standalone_expansions; limit 20;")
            }
            val bodyString = response.body<String>()
            
            if (response.status != HttpStatusCode.OK) {
                Log.e("IgdbClient", "IGDB Search Error Status: ${response.status} - Body: $bodyString")
                return emptyList()
            }
            
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
                setBody("fields id, name, game_type, first_release_date, cover.url, genres.name, summary, screenshots.url, url, rating, aggregated_rating, involved_companies.company.name, involved_companies.developer, involved_companies.publisher, themes.name, keywords.name, external_games.url, external_games.category, external_games.uid, game_modes.name, franchises.name, collection.name, collections.name, parent_game, dlcs, expansions, bundles, standalone_expansions; where id = ($idsString); limit 500;")
            }
            val bodyString = response.body<String>()
            
            if (response.status != HttpStatusCode.OK) {
                Log.e("IgdbClient", "IGDB GetByIds Error Status: ${response.status} - Body: $bodyString")
                return emptyList()
            }

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

    suspend fun getExternalGamesForGame(clientId: String, gameId: Long): List<IgdbExternalGameData> {
        val token = accessToken ?: return emptyList()

        return try {
            val response = client.post("https://api.igdb.com/v4/external_games") {
                header("Client-ID", clientId)
                header("Authorization", "Bearer $token")
                setBody("fields game, category, uid, url; where game = ($gameId); limit 200;")
            }
            val body = response.bodyAsText()
            Log.d("IgdbClient", "IGDB Raw Externals for $gameId: $body")
            
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val externals = json.decodeFromString<List<IgdbExternalGameData>>(body)
            Log.d("IgdbClient", "IGDB ExternalGames: game $gameId -> ${externals.size} entries")
            externals
        } catch (e: Exception) {
            Log.e("IgdbClient", "IGDB ExternalGamesForGame Error: ${e.message}", e)
            emptyList()
        }
    }

    fun getFullCoverUrl(thumbUrl: String): String {
        // IGDB urls usually look like //images.igdb.com/igdb/image/upload/t_thumb/co1r8v.jpg
        // We want to change t_thumb to t_cover_big
        return "https:" + thumbUrl.replace("t_thumb", "t_cover_big")
    }

    suspend fun getGameTimeToBeat(clientId: String, gameId: Long): IgdbGameTimeToBeat? {
        val token = accessToken ?: return null
        
        // Try multiple field variants for 'game_id' just in case
        val variants = listOf("game_id", "game")
        
        for (field in variants) {
            try {
                val response = client.post("https://api.igdb.com/v4/game_time_to_beats") {
                    header("Client-ID", clientId)
                    header("Authorization", "Bearer $token")
                    setBody("fields hastily, normally, completely; where $field = $gameId;")
                }
                
                if (response.status == HttpStatusCode.OK) {
                    val body = response.bodyAsText()
                    Log.d("IgdbClient", "IGDB TTB Raw Response ($field): $body")
                    val list: List<IgdbGameTimeToBeat> = Json { ignoreUnknownKeys = true }.decodeFromString(body)
                    if (list.isNotEmpty()) return list.first()
                } else {
                    val errorBody = response.bodyAsText()
                    Log.e("IgdbClient", "IGDB TTB Error ($field) Status: ${response.status} - Body: $errorBody")
                }
            } catch (e: Exception) {
                Log.e("IgdbClient", "IGDB TTB Error ($field): ${e.message}")
            }
        }
        return null
    }
}
