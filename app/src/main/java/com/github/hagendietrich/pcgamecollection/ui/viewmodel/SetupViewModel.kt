package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hagendietrich.pcgamecollection.data.api.IgdbClient
import com.github.hagendietrich.pcgamecollection.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel(
    private val settingsRepository: SettingsRepository,
    private val igdbClient: IgdbClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<SetupUiState>(SetupUiState.Idle)
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    fun saveSettings(clientId: String, clientSecret: String, steamApiKey: String) {
        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            
            // 1. Handle Steam API Key (always save if provided, regardless of IGDB)
            if (steamApiKey.isNotBlank()) {
                settingsRepository.saveSteamApiKey(steamApiKey)
            }

            // 2. Handle IGDB Credentials
            if (clientId.isNotBlank() && clientSecret.isNotBlank()) {
                // If provided, attempt to connect and verify
                val success = igdbClient.authenticate(clientId, clientSecret)
                if (success) {
                    settingsRepository.saveIgdbCredentials(clientId, clientSecret)
                    _uiState.value = SetupUiState.Success
                } else {
                    _uiState.value = SetupUiState.Error("Invalid IGDB credentials. Please check your Client ID and Secret.")
                }
            } else if (steamApiKey.isNotBlank()) {
                // If IGDB is blank but Steam was provided, we consider it a success for the Steam update
                _uiState.value = SetupUiState.Success
            } else {
                // Everything is blank
                _uiState.value = SetupUiState.Error("Please enter IGDB credentials or a Steam API Key.")
            }
        }
    }

    // Keep for backward compatibility if needed, but redirects to saveSettings
    fun saveAndConnect(clientId: String, clientSecret: String, steamApiKey: String = "") {
        saveSettings(clientId, clientSecret, steamApiKey)
    }
}

sealed class SetupUiState {
    object Idle : SetupUiState()
    object Loading : SetupUiState()
    object Success : SetupUiState()
    data class Error(val message: String) : SetupUiState()
}
