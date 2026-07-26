package com.example.digitalcollectionmanager.data.database

import androidx.room.TypeConverter
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters to allow storing complex types like List<String> in the database.
 * It serializes the list to a JSON string for storage and deserializes it back.
 */
class Converters {
    @TypeConverter
    fun fromList(value: List<String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toList(value: String): List<String> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromStatus(status: CompletionStatus): String {
        return status.name
    }

    @TypeConverter
    fun toStatus(value: String): CompletionStatus {
        return try {
            CompletionStatus.valueOf(value)
        } catch (e: Exception) {
            CompletionStatus.BACKLOG
        }
    }
}
