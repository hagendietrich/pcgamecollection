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
    val id: Long = 0,
    val name: String = "",
    @SerialName("game_type") val category: Int? = null,
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
    @SerialName("game_modes") val gameModes: List<IgdbGameMode>? = null,
    val franchises: List<IgdbFranchise>? = null,
    val collection: IgdbCollection? = null,
    val collections: List<IgdbCollection>? = null,
    @SerialName("parent_game") val parentGame: Long? = null,
    val dlcs: List<Long>? = null,
    val expansions: List<Long>? = null,
    val bundles: List<Long>? = null,
    @SerialName("standalone_expansions") val standaloneExpansions: List<Long>? = null
)

@Serializable
data class IgdbGameTimeToBeat(
    val id: Long? = null,
    val hastily: Int = 0,
    @SerialName("hastly") val hastly: Int = 0, // Fallback for typo
    val normally: Int = 0,
    val completely: Int = 0,
    @SerialName("game_id") val gameId: Long? = null
) {
    val bestHastly: Int get() = if (hastily > 0) hastily else hastly
}

@Serializable
data class IgdbCollection(
    val id: Long? = null,
    val name: String? = null
)

@Serializable
data class IgdbFranchise(
    val id: Long? = null,
    val name: String = ""
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
