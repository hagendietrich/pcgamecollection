package com.github.hagendietrich.pcgamecollection.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryViewSnapshot(
    val name: String,
    val savedAt: Long,
    val searchTerm: String,
    val grouping: GroupingType,
    val filters: LibraryFilters,
    val sortOrder: SortOrder,
    val columns: Int
)
