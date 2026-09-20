package com.github.hagendietrich.pcgamecollection.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.hagendietrich.pcgamecollection.data.model.GroupingType
import com.github.hagendietrich.pcgamecollection.data.model.LibraryFilters
import com.github.hagendietrich.pcgamecollection.data.model.SortOrder
import com.github.hagendietrich.pcgamecollection.dataStore
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
        val LAST_EA_EMAIL = stringPreferencesKey("last_ea_email")
        val EPIC_EMAIL = stringPreferencesKey("epic_email")
        val EPIC_ACCOUNT_ID = stringPreferencesKey("epic_account_id")
        val UBISOFT_EMAIL = stringPreferencesKey("ubisoft_email")
        val GOG_USER_ID = stringPreferencesKey("gog_user_id")
        val EA_REMID = stringPreferencesKey("ea_remid")
        val EA_SID = stringPreferencesKey("ea_sid")
        val EA_ACCESS_TOKEN = stringPreferencesKey("ea_access_token")
        val COLUMN_COUNT = intPreferencesKey("column_count")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val GROUPING_TYPE = stringPreferencesKey("grouping_type") // "NONE", "STATUS", "LABEL", "PLATFORM", "GENRE"
        val LIBRARY_FILTERS = stringPreferencesKey("library_filters")
        val CURRENT_SEARCH_TERM = stringPreferencesKey("current_search_term")
        val SAVED_GROUPING = stringPreferencesKey("saved_grouping")
        val SAVED_FILTERS = stringPreferencesKey("saved_filter")
        val SAVED_SORT_ORDER = stringPreferencesKey("saved_sort_order")
        val SAVED_SEARCH_TERM = stringPreferencesKey("saved_search_term")
        val SAVED_COLUMN_COUNT = intPreferencesKey("save_column_count")
    }

    suspend fun saveLibrarySnapshot(
        searchTerm: String,
        grouping: GroupingType,
        filters: LibraryFilters,
        sortOrder: SortOrder,
        columns: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.SAVED_SEARCH_TERM] = searchTerm
            prefs[PreferencesKeys.SAVED_GROUPING] = grouping.name
            prefs[PreferencesKeys.SAVED_FILTERS] = Json.encodeToString(filters)
            prefs[PreferencesKeys.SAVED_SORT_ORDER] = sortOrder.name
            prefs[PreferencesKeys.SAVED_COLUMN_COUNT] = columns
        }
    }

    suspend fun restoreLibrarySnapshot(): String? {
        var savedSearchTerm: String? = null
        context.dataStore.edit { prefs ->
            savedSearchTerm = prefs[PreferencesKeys.SAVED_SEARCH_TERM]
            
            prefs[PreferencesKeys.SAVED_GROUPING]?.let { 
                prefs[PreferencesKeys.GROUPING_TYPE] = it 
            }
            prefs[PreferencesKeys.SAVED_FILTERS]?.let { 
                prefs[PreferencesKeys.LIBRARY_FILTERS] = it 
            }
            prefs[PreferencesKeys.SAVED_SORT_ORDER]?.let { 
                prefs[PreferencesKeys.SORT_ORDER] = it 
            }
            prefs[PreferencesKeys.SAVED_COLUMN_COUNT]?.let { 
                prefs[PreferencesKeys.COLUMN_COUNT] = it 
            }
        }
        return savedSearchTerm
    }

    val columnCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COLUMN_COUNT] ?: 3 // Default to 3 columns
    }

    suspend fun updateColumnCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLUMN_COUNT] = count.coerceIn(1, 20) // Keep it between 1 and 20
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

    suspend fun addFilter(category: String, item: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[PreferencesKeys.LIBRARY_FILTERS]
            val currentFilters = try {
                if (currentJson != null) Json.decodeFromString<LibraryFilters>(currentJson) else LibraryFilters()
            } catch (e: Exception) {
                LibraryFilters()
            }

            val newFilters = when (category) {
                "Mode" -> currentFilters.copy(modes = currentFilters.modes + (item to com.github.hagendietrich.pcgamecollection.data.model.FilterType.INCLUDE))
                "Platform" -> currentFilters.copy(platforms = currentFilters.platforms + (item to com.github.hagendietrich.pcgamecollection.data.model.FilterType.INCLUDE))
                "Status" -> currentFilters.copy(statuses = currentFilters.statuses + (item to com.github.hagendietrich.pcgamecollection.data.model.FilterType.INCLUDE))
                "Labels" -> currentFilters.copy(labels = currentFilters.labels + (item to com.github.hagendietrich.pcgamecollection.data.model.FilterType.INCLUDE))
                "Genre" -> currentFilters.copy(genres = currentFilters.genres + (item to com.github.hagendietrich.pcgamecollection.data.model.FilterType.INCLUDE))
                else -> currentFilters
            }

            preferences[PreferencesKeys.LIBRARY_FILTERS] = Json.encodeToString(newFilters)
        }
    }

    val currentSearchTerm: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CURRENT_SEARCH_TERM] ?: ""
    }

    suspend fun updateCurrentSearchTerm(term: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CURRENT_SEARCH_TERM] = term
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

    val lastEaEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LAST_EA_EMAIL]
    }

    val epicEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EPIC_EMAIL]
    }

    val epicAccountId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EPIC_ACCOUNT_ID]
    }

    val gogUserId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.GOG_USER_ID]
    }

    val ubisoftEmail: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.UBISOFT_EMAIL]
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val encryptedPrefs = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getEpicPassword(): String? {
        return encryptedPrefs.getString("epic_password", null)
    }

    fun getUbisoftPassword(): String? {
        return encryptedPrefs.getString("ubisoft_password", null)
    }

    suspend fun saveEpicCredentials(email: String, password: String?, accountId: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EPIC_EMAIL] = email.trim()
            if (accountId != null) {
                preferences[PreferencesKeys.EPIC_ACCOUNT_ID] = accountId
            }
        }
        if (password != null) {
            encryptedPrefs.edit().putString("epic_password", password.trim()).apply()
        }
    }

    suspend fun saveGogUserId(userId: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.GOG_USER_ID] = userId
        }
    }

    suspend fun saveUbisoftCredentials(email: String, password: String?) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.UBISOFT_EMAIL] = email.trim()
        }
        if (password != null) {
            encryptedPrefs.edit().putString("ubisoft_password", password.trim()).apply()
        }
    }

    val eaRemid: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EA_REMID]
    }

    val eaSid: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EA_SID]
    }

    val eaAccessToken: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.EA_ACCESS_TOKEN]
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

    suspend fun saveLastEaEmail(email: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_EA_EMAIL] = email
        }
    }

    suspend fun saveEaTokens(remid: String, sid: String, accessToken: String? = null) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EA_REMID] = remid
            preferences[PreferencesKeys.EA_SID] = sid
            if (accessToken != null) {
                preferences[PreferencesKeys.EA_ACCESS_TOKEN] = accessToken
            }
        }
    }

    suspend fun saveEaAccessToken(token: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.EA_ACCESS_TOKEN] = token
        }
    }

    suspend fun clearEaTokens() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.EA_REMID)
            preferences.remove(PreferencesKeys.EA_SID)
            preferences.remove(PreferencesKeys.EA_ACCESS_TOKEN)
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
