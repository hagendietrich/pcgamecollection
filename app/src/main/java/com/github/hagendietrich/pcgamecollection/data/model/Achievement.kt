package com.github.hagendietrich.pcgamecollection.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Achievement(
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val isUnlocked: Boolean = false,
    val unlockTime: Long? = null, // Unix timestamp
    val isHidden: Boolean = false
)
