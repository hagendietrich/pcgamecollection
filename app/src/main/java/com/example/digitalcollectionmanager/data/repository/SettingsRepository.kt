package com.example.digitalcollectionmanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.digitalcollectionmanager.data.model.GroupingType
import com.example.digitalcollectionmanager.data.model.LibraryFilters
import com.example.digitalcollectionmanager.data.model.SortOrder
import com.example.digitalcollectionmanager.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val CLIENT_ID = stringPreferencesKey("igdb_client_id")
        val CLIENT_SECRET = stringPreferencesKey("igdb_client_secret")
        val STEAM_API_KEY = stringPreferencesKey("steam_api_key")
        val LAST_STEAM_ID = stringPreferencesKey("last_steam_id")
        val LAST_GOG_USERNAME = stringPreferencesKey("last_gog_username")
        val COLUMN_COUNT = androidx.datastore.preferences.core.intPreferencesKey("column_count")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val GROUPING_TYPE = stringPreferencesKey("grouping_type") // "NONE", "STATUS", "LABEL", "PLATFORM", "GENRE"
        val LIBRARY_FILTERS = stringPreferencesKey("library_filters")
    }

    val columnCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COLUMN_COUNT] ?: 3 // Default to 3 columns
    }

    suspend fun updateColumnCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLUMN_COUNT] = count.coerceIn(1, 10) // Keep it between 1 and 10
        }
    }

    val sortOrder: Flow<SortOrder> = context.dataStore.data.map { preferences ->
        val orderName = preferences[PreferencesKeys.SORT_ORDER] ?: SortOrder.TITLE_ASC.name
        try {
            SortOrder.valueOf(orderName)
        } catch (e: Exception) {
            SortOrder.TITLE_ASC
        }
    }

    suspend fun updateSortOrder(order: SortOrder) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SORT_ORDER] = order.name
        }
    }

    val groupingType: Flow<GroupingType> = context.dataStore.data.map { preferences ->
        val typeName = preferences[PreferencesKeys.GROUPING_TYPE] ?: GroupingType.NONE.name
        try {
            GroupingType.valueOf(typeName)
        } catch (e: Exception) {
            GroupingType.NONE
        }
    }

    suspend fun updateGroupingType(type: GroupingType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GROUPING_TYPE] = type.name
        }
    }

    val libraryFilters: Flow<LibraryFilters> = context.dataStore.data.map { preferences ->
        val jsonStr = preferences[PreferencesKeys.LIBRARY_FILTERS] ?: return@map LibraryFilters()
        try {
            Json.decodeFromString<LibraryFilters>(jsonStr)
        } catch (e: Exception) {
            LibraryFilters()
        }
    }

    suspend fun updateFilters(filters: LibraryFilters) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIBRARY_FILTERS] = Json.encodeToString(filters)
        }
    }

    val clientId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLIENT_ID]
    }

    val clientSecret: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLIENT_SECRET]
    }

    val steamApiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.STEAM_API_KEY]
    }

    val lastSteamId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_STEAM_ID]
    }

    val lastGogUsername: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_GOG_USERNAME]
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[PreferencesKeys.CLIENT_ID].isNullOrBlank() && 
        !preferences[PreferencesKeys.CLIENT_SECRET].isNullOrBlank()
    }

    suspend fun saveIgdbCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] = clientId
            preferences[PreferencesKeys.CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun saveSteamApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEAM_API_KEY] = apiKey
        }
    }

    suspend fun saveLastSteamId(id: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_STEAM_ID] = id
        }
    }

    suspend fun saveLastGogUsername(name: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_GOG_USERNAME] = name
        }
    }

    suspend fun saveCredentials(clientId: String, clientSecret: String, steamApiKey: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] = clientId
            preferences[PreferencesKeys.CLIENT_SECRET] = clientSecret
            if (steamApiKey != null) {
                preferences[PreferencesKeys.STEAM_API_KEY] = steamApiKey
            }
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CLIENT_ID)
            preferences.remove(PreferencesKeys.CLIENT_SECRET)
        }
    }
}
