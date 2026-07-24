package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
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

    fun saveAndConnect(clientId: String, clientSecret: String) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            _uiState.value = SetupUiState.Error("Please enter both Client ID and Client Secret")
            return
        }

        viewModelScope.launch {
            _uiState.value = SetupUiState.Loading
            val success = igdbClient.authenticate(clientId, clientSecret)
            if (success) {
                settingsRepository.saveCredentials(clientId, clientSecret)
                _uiState.value = SetupUiState.Success
            } else {
                _uiState.value = SetupUiState.Error("Invalid credentials. Please check your Client ID and Secret.")
            }
        }
    }
}

sealed class SetupUiState {
    object Idle : SetupUiState()
    object Loading : SetupUiState()
    object Success : SetupUiState()
    data class Error(val message: String) : SetupUiState()
}
