package com.github.hagendietrich.pcgamecollection.data.model

import kotlinx.serialization.Serializable

/**
 * Represents the completion progress of a game.
 * Ordered from highest priority/completion to lowest.
 */
@Serializable
enum class CompletionStatus(val priority: Int) {
    COMPLETED(0),
    PLAYING(1),
    ON_HOLD(2),
    BACKLOG(3),
    ABANDONED(4)
}
