package com.github.hagendietrich.pcgamecollection.data.database

import androidx.room.TypeConverter
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters to allow storing complex types in the database.
 * It serializes objects to JSON strings for storage and deserializes them back.
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
    fun fromMapStringString(value: Map<String, String>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toMapStringString(value: String): Map<String, String> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromMapStringInt(value: Map<String, Int>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toMapStringInt(value: String): Map<String, Int> {
        return try {
            Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
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
