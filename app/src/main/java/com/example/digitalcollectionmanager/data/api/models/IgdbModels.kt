package com.example.digitalcollectionmanager.data.api.models

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
    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
    val cover: IgdbCover? = null,
    val genres: List<IgdbGenre>? = null,
    val summary: String? = null,
    val screenshots: List<IgdbScreenshot>? = null,
    val url: String? = null
)

@Serializable
data class IgdbGenre(
    val id: Long,
    val name: String
)

@Serializable
data class IgdbScreenshot(
    val id: Long,
    val url: String
)

@Serializable
data class IgdbCover(
    val id: Long,
    val url: String
)
