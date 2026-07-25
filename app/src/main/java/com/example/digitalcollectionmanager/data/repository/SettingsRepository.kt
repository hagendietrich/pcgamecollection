package com.example.digitalcollectionmanager.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.digitalcollectionmanager.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val CLIENT_ID = stringPreferencesKey("igdb_client_id")
        val CLIENT_SECRET = stringPreferencesKey("igdb_client_secret")
        val COLUMN_COUNT = androidx.datastore.preferences.core.intPreferencesKey("column_count")
    }

    val columnCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.COLUMN_COUNT] ?: 3 // Default to 3 columns
    }

    suspend fun updateColumnCount(count: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.COLUMN_COUNT] = count.coerceIn(2, 5) // Keep it between 2 and 5
        }
    }

    val clientId: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLIENT_ID]
    }

    val clientSecret: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.CLIENT_SECRET]
    }

    val isConfigured: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !preferences[PreferencesKeys.CLIENT_ID].isNullOrBlank() && 
        !preferences[PreferencesKeys.CLIENT_SECRET].isNullOrBlank()
    }

    suspend fun saveCredentials(clientId: String, clientSecret: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CLIENT_ID] = clientId
            preferences[PreferencesKeys.CLIENT_SECRET] = clientSecret
        }
    }

    suspend fun clearCredentials() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.CLIENT_ID)
            preferences.remove(PreferencesKeys.CLIENT_SECRET)
        }
    }
}
