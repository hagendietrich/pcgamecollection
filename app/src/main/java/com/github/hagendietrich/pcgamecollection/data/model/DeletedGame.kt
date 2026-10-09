package com.github.hagendietrich.pcgamecollection.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "deleted_games")
data class DeletedGame(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val igdbId: Long? = null,
    val title: String,
    val deletedAt: Long = System.currentTimeMillis()
)
