package com.github.hagendietrich.pcgamecollection.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class FilterType {
    INCLUDE, // (+)
    EXCLUDE, // (-)
    NONE     // Unchecked
}

@Serializable
data class LibraryFilters(
    val modes: Map<String, FilterType> = emptyMap(),
    val platforms: Map<String, FilterType> = emptyMap(),
    val statuses: Map<String, FilterType> = emptyMap(),
    val labels: Map<String, FilterType> = emptyMap(),
    val genres: Map<String, FilterType> = emptyMap(),
    val releaseYearStart: Int? = null,
    val releaseYearEnd: Int? = null,
    val playtimeMinHours: Int? = null,
    val playtimeMaxHours: Int? = null
) {
    fun isCategoryActive(category: String): Boolean {
        return when (category) {
            "Mode" -> modes.values.any { it != FilterType.NONE }
            "Platform" -> platforms.values.any { it != FilterType.NONE }
            "Status" -> statuses.values.any { it != FilterType.NONE }
            "Labels" -> labels.values.any { it != FilterType.NONE }
            "Genre" -> genres.values.any { it != FilterType.NONE }
            "ReleaseYear" -> releaseYearStart != null || releaseYearEnd != null
            "Playtime" -> playtimeMinHours != null || playtimeMaxHours != null
            else -> false
        }
    }

    fun hasAnyActiveFilters(): Boolean {
        return modes.values.any { it != FilterType.NONE } ||
               platforms.values.any { it != FilterType.NONE } ||
               statuses.values.any { it != FilterType.NONE } ||
               labels.values.any { it != FilterType.NONE } ||
               genres.values.any { it != FilterType.NONE } ||
               releaseYearStart != null || releaseYearEnd != null ||
               playtimeMinHours != null || playtimeMaxHours != null
    }
}
