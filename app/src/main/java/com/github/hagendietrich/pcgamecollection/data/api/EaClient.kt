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
import kotlinx.serialization.json.*

class EaClient {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            })
        }
        defaultRequest {
            header("User-Agent", "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36")
        }
    }

    companion object {
        const val EA_CLIENT_ID = "ORIGIN_SPA_ID"
        const val EA_REDIRECT_URI = "https://www.origin.com/views/login.html"
    }

    @Serializable
    data class EaIdentity(val pid: EaPid? = null)

    @Serializable
    data class EaPid(val pidId: Long? = null)

    @Serializable
    data class EaEntitlements(val entitlements: List<EaEntitlement> = emptyList())

    @Serializable
    data class EaEntitlement(val offerId: String)

    @Serializable
    data class EaOfferResponse(
        val baseAttributes: EaBaseAttributes? = null,
        val localizableAttributes: EaLocalizableAttributes? = null
    )

    @Serializable
    data class EaBaseAttributes(val displayName: String? = null)

    @Serializable
    data class EaLocalizableAttributes(val displayName: String? = null)

    @Serializable
    data class EaTokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("token_type") val tokenType: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null
    )

    @Serializable
    data class EaGraphqlResponse(val data: EaGraphqlData? = null)

    @Serializable
    data class EaGraphqlData(val me: EaGraphqlMe? = null)

    @Serializable
    data class EaGraphqlMe(val ownedGameProducts: EaGraphqlProducts? = null)

    @Serializable
    data class EaGraphqlProducts(
        val next: String? = null,
        val items: List<EaGraphqlItem> = emptyList()
    )

    @Serializable
    data class EaGraphqlItem(
        val originOfferId: String? = null,
        val product: EaGraphqlProduct? = null
    )

    @Serializable
    data class EaGraphqlProduct(
        val name: String? = null,
        val baseItem: EaGraphqlBaseItem? = null
    )

    @Serializable
    data class EaGraphqlBaseItem(val gameType: String? = null)
    
    suspend fun exchangeCodeForToken(code: String): String? {
        return try {
            val response = client.submitForm(
                url = "https://accounts.ea.com/connect/token",
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("client_id", EA_CLIENT_ID)
                    append("redirect_uri", EA_REDIRECT_URI)
                }
            )
            if (response.status == HttpStatusCode.OK) {
                response.body<EaTokenResponse>().accessToken
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun fetchTokenWithCookies(remid: String, sid: String): String? {
        val clientId = "ORIGIN_JS_SDK"
        val redirectUri = "nucleus:rest"
        return try {
            val response = client.get("https://accounts.ea.com/connect/auth") {
                parameter("client_id", clientId)
                parameter("response_type", "token")
                parameter("redirect_uri", redirectUri)
                parameter("prompt", "none")
                header("Cookie", "remid=$remid; sid=$sid")
            }
            if (response.status == HttpStatusCode.OK) {
                val json: JsonObject = response.body()
                json["access_token"]?.jsonPrimitive?.content
            } else null
        } catch (e: Exception) { null }
    }

    suspend fun fetchGamesWithGraphql(accessToken: String): List<Pair<String, String>> {
        val games = mutableListOf<Pair<String, String>>()
        var nextCursor: String? = null
        val limit = 100
        do {
            try {
                val query = """
                    query getEntitlements(${'$'}limit: Int, ${'$'}next: String) {
                      me {
                        ownedGameProducts(
                          locale: "DEFAULT", entitlementEnabled: true,
                          storefronts: [EA], type: [DIGITAL_FULL_GAME, PACKAGED_FULL_GAME],
                          platforms: [PC], paging: { limit: ${'$'}limit, next: ${'$'}next }
                        ) { 
                          next, 
                          items { 
                            originOfferId 
                            product { 
                              name
                              baseItem { gameType } 
                            } 
                          } 
                        }
                      }
                    }
                """.trimIndent()
                val response = client.post("https://service-aggregation-layer.juno.ea.com/graphql") {
                    applyEaAuth(accessToken)
                    contentType(ContentType.Application.Json)
                    setBody(buildJsonObject {
                        put("query", query)
                        put("variables", buildJsonObject {
                            put("limit", limit)
                            put("next", nextCursor)
                        })
                    })
                }
                if (response.status == HttpStatusCode.OK) {
                    val graphqlResponse: EaGraphqlResponse = response.body()
                    val products = graphqlResponse.data?.me?.ownedGameProducts
                    products?.items?.forEach { item ->
                        val name = item.product?.name
                        val offerId = item.originOfferId
                        if (name != null && offerId != null) games.add(name to offerId)
                    }
                    nextCursor = products?.next
                } else break
            } catch (e: Exception) { break }
        } while (nextCursor != null)
        return games
    }

    private fun HttpRequestBuilder.applyEaAuth(token: String) {
        header("Authorization", "Bearer $token")
        header("AuthToken", token)
        header("X-AuthToken", token)
    }

    suspend fun fetchOwnedGames(remid: String, sid: String, accessToken: String? = null): List<String> {
        return try {
            val identityResponse = client.get("https://gateway.ea.com/proxy/identity/pids/me") {
                if (!accessToken.isNullOrBlank()) applyEaAuth(accessToken)
                else {
                    header("Cookie", "remid=$remid; sid=$sid")
                    header("x-ea-app-id", EA_CLIENT_ID)
                }
            }
            if (identityResponse.status == HttpStatusCode.OK) {
                val identity: JsonObject = identityResponse.body()
                val pidId = identity["pid"]?.jsonObject?.get("pidId")?.jsonPrimitive?.longOrNull ?: return emptyList()
                val entitlementsResponse = client.get("https://gateway.ea.com/proxy/identity/pids/$pidId/entitlements?itemType=DEFAULT") {
                    if (!accessToken.isNullOrBlank()) applyEaAuth(accessToken)
                    else header("Cookie", "remid=$remid; sid=$sid")
                }
                if (entitlementsResponse.status == HttpStatusCode.OK) {
                    val json: JsonObject = entitlementsResponse.body()
                    json["entitlements"]?.jsonArray?.mapNotNull { it.jsonObject["offerId"]?.jsonPrimitive?.content }?.distinct() ?: emptyList()
                } else emptyList()
            } else emptyList()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun fetchGameTitle(offerId: String): String? {
        return try {
            val response: JsonObject = client.get("https://api1.origin.com/ecommerce2/public/$offerId/en_US").body()
            response["localizableAttributes"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
                ?: response["baseAttributes"]?.jsonObject?.get("displayName")?.jsonPrimitive?.content
        } catch (e: Exception) { null }
    }
}
