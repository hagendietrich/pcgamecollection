package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class ImportViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _lastSteamId = MutableStateFlow("")
    val lastSteamId: StateFlow<String> = _lastSteamId.asStateFlow()

    private val _lastGogUsername = MutableStateFlow("")
    val lastGogUsername: StateFlow<String> = _lastGogUsername.asStateFlow()

    init {
        viewModelScope.launch {
            _lastSteamId.value = settingsRepository.lastSteamId.firstOrNull() ?: ""
            _lastGogUsername.value = settingsRepository.lastGogUsername.firstOrNull() ?: ""
        }
    }

    fun importSteam(profileUrl: String) {
        if (profileUrl.isBlank()) return
        viewModelScope.launch {
            settingsRepository.saveLastSteamId(profileUrl)
            _uiState.value = ImportUiState.Loading(0f, "Initializing Steam import...")
            try {
                val count = gameRepository.syncSteamGames(profileUrl) { progress, message ->
                    _uiState.value = ImportUiState.Loading(progress, message)
                }
                when {
                    count == -1 -> {
                        _uiState.value = ImportUiState.Error("Steam API Key is missing. Please configure it in Settings.")
                    }
                    count > 0 -> {
                        _uiState.value = ImportUiState.Success("Successfully imported $count games from Steam.")
                    }
                    else -> {
                        _uiState.value = ImportUiState.Error("No games found. Check your Steam ID or Privacy Settings.")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Steam import failed: ${e.message}")
            }
        }
    }

    fun importGog(username: String) {
        if (username.isBlank()) return
        viewModelScope.launch {
            settingsRepository.saveLastGogUsername(username)
            _uiState.value = ImportUiState.Loading(0f, "Initializing GOG import...")
            try {
                val count = gameRepository.syncGogGames(username) { progress, message ->
                    _uiState.value = ImportUiState.Loading(progress, message)
                }
                if (count > 0) {
                    _uiState.value = ImportUiState.Success("Successfully imported $count games from GOG.")
                } else {
                    _uiState.value = ImportUiState.Error("No games found. Ensure your profile is public.")
                }
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("GOG import failed: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportUiState.Idle
    }
}

sealed class ImportUiState {
    object Idle : ImportUiState()
    data class Loading(val progress: Float, val message: String) : ImportUiState()
    data class Success(val message: String) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}
