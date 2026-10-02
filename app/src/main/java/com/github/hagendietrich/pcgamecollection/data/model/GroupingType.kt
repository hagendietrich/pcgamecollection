package com.github.hagendietrich.pcgamecollection.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class GroupingType {
    NONE,
    STATUS,
    LABEL,
    PLATFORM,
    GENRE,
    SERIES,
    FRANCHISE
}
