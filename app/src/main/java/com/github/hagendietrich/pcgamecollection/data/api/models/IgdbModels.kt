package com.github.hagendietrich.pcgamecollection.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IgdbTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
data class IgdbGame(
    val id: Long,
    val name: String,
    val category: Int = 0,
    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
    val cover: IgdbCover? = null,
    val genres: List<IgdbGenre>? = null,
    val summary: String? = null,
    val screenshots: List<IgdbScreenshot>? = null,
    val url: String? = null,
    val rating: Double? = null,
    @SerialName("aggregated_rating") val aggregatedRating: Double? = null,
    @SerialName("involved_companies") val involvedCompanies: List<IgdbInvolvedCompany>? = null,
    val themes: List<IgdbTheme>? = null,
    val keywords: List<IgdbKeyword>? = null,
    @SerialName("external_games") val externalGames: List<IgdbExternalGameData>? = null,
    @SerialName("game_modes") val gameModes: List<IgdbGameMode>? = null
)

@Serializable
data class IgdbGameMode(
    val id: Long? = null,
    val name: String = ""
)

@Serializable
data class IgdbInvolvedCompany(
    val id: Long? = null,
    val company: IgdbCompany? = null,
    val developer: Boolean = false,
    val publisher: Boolean = false
)

@Serializable
data class IgdbCompany(
    val id: Long? = null,
    val name: String = "Unknown"
)

@Serializable
data class IgdbTheme(
    val id: Long? = null,
    val name: String = ""
)

@Serializable
data class IgdbKeyword(
    val id: Long? = null,
    val name: String = ""
)

@Serializable
data class IgdbExternalGameData(
    val id: Long? = null,
    val category: Int = 0,
    val url: String? = null,
    val uid: String? = null
)

@Serializable
data class IgdbGenre(
    val id: Long? = null,
    val name: String = ""
)

@Serializable
data class IgdbScreenshot(
    val id: Long? = null,
    val url: String = ""
)

@Serializable
data class IgdbCover(
    val id: Long? = null,
    val url: String = ""
)
