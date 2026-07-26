package com.example.digitalcollectionmanager.data.model

/**
 * Represents the completion progress of a game.
 * Ordered from highest priority/completion to lowest.
 */
enum class CompletionStatus(val priority: Int) {
    COMPLETED(0),
    PLAYING(1),
    ON_HOLD(2),
    BACKLOG(3),
    ABANDONED(4)
}
