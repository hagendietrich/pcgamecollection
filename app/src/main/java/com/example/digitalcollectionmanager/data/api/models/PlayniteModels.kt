package com.example.digitalcollectionmanager.data.api.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayniteGame(
    @SerialName("Name") val name: String,
    @SerialName("Playtime") val playtime: Long = 0,
    @SerialName("Source") val source: PlayniteSource? = null,
    @SerialName("CompletionStatus") val completionStatus: PlayniteStatus? = null,
    @SerialName("Platforms") val platforms: List<PlaynitePlatform>? = null,
    @SerialName("ReleaseDate") val releaseDate: PlayniteReleaseDate? = null,
    @SerialName("GameId") val gameId: String? = null
)

@Serializable
data class PlayniteSource(
    @SerialName("Name") val name: String
)

@Serializable
data class PlayniteStatus(
    @SerialName("Name") val name: String
)

@Serializable
data class PlaynitePlatform(
    @SerialName("Name") val name: String
)

@Serializable
data class PlayniteReleaseDate(
    @SerialName("ReleaseDate") val releaseDate: String? = null
)
