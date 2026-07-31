package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.data.repository.UnmatchedGame
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SyncViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<SyncUiState>(SyncUiState.Idle)
    val uiState: StateFlow<SyncUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

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

    fun syncSteam(profileUrl: String) {
        if (profileUrl.isBlank()) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            settingsRepository.saveLastSteamId(profileUrl)
            _uiState.value = SyncUiState.Loading(0f, "Initializing Steam sync...")
            try {
                val result = gameRepository.syncSteamGames(profileUrl) { progress, message ->
                    _uiState.value = SyncUiState.Loading(progress, message)
                }
                when {
                    result.importedCount == -1 -> {
                        _uiState.value = SyncUiState.Error("Steam API Key is missing. Please configure it in Settings.")
                    }
                    result.importedCount >= 0 -> {
                        val msg = if (result.importedCount > 0) 
                            "Successfully synced ${result.importedCount} games from Steam."
                        else "Steam library up to date. No new games were added."
                        
                        _uiState.value = SyncUiState.Success(msg, result.unmatchedGames)
                    }
                    else -> {
                        _uiState.value = SyncUiState.Error("No games found. Check your Steam ID or Privacy Settings.")
                    }
                }
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("Steam sync failed: ${e.message}")
            }
        }
    }

    fun syncGog(username: String) {
        if (username.isBlank()) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            settingsRepository.saveLastGogUsername(username)
            _uiState.value = SyncUiState.Loading(0f, "Initializing GOG sync...")
            try {
                val result = gameRepository.syncGogGames(username) { progress, message ->
                    _uiState.value = SyncUiState.Loading(progress, message)
                }
                if (result.importedCount >= 0) {
                    val msg = if (result.importedCount > 0)
                        "Successfully synced ${result.importedCount} games from GOG."
                    else "GOG library up to date. No new games were added."
                    
                    _uiState.value = SyncUiState.Success(msg, result.unmatchedGames)
                } else {
                    _uiState.value = SyncUiState.Error("No games found. Ensure your profile is public.")
                }
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("GOG sync failed: ${e.message}")
            }
        }
    }

    fun syncAllAccounts() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val steamId = _lastSteamId.value
            val gogUser = _lastGogUsername.value
            
            if (steamId.isBlank() && gogUser.isBlank()) {
                _uiState.value = SyncUiState.Error("No accounts connected. Please provide a Steam ID or GOG Username.")
                return@launch
            }

            var totalImported = 0
            val allUnmatched = mutableListOf<UnmatchedGame>()
            
            try {
                // Step 1: Steam
                if (steamId.isNotBlank()) {
                    _uiState.value = SyncUiState.Loading(0f, "Starting Steam sync...")
                    val steamResult = gameRepository.syncSteamGames(steamId) { progress, message ->
                        _uiState.value = SyncUiState.Loading(progress * 0.5f, "Steam: $message")
                    }
                    if (steamResult.importedCount >= 0) totalImported += steamResult.importedCount
                    allUnmatched.addAll(steamResult.unmatchedGames)
                }

                // Step 2: GOG
                if (gogUser.isNotBlank()) {
                    _uiState.value = SyncUiState.Loading(0.5f, "Starting GOG sync...")
                    val gogResult = gameRepository.syncGogGames(gogUser) { progress, message ->
                        _uiState.value = SyncUiState.Loading(0.5f + (progress * 0.5f), "GOG: $message")
                    }
                    if (gogResult.importedCount >= 0) totalImported += gogResult.importedCount
                    allUnmatched.addAll(gogResult.unmatchedGames)
                }

                val msg = "Sync All complete. Total new games: $totalImported"
                _uiState.value = SyncUiState.Success(msg, allUnmatched)

            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("Sync All failed: ${e.message}")
            }
        }
    }

    fun cancelSync() {
        syncJob?.cancel()
        _uiState.value = SyncUiState.Idle
    }

    fun resolveUnmatchedGame(unmatched: UnmatchedGame, selection: IgdbGame) {
        viewModelScope.launch {
            gameRepository.linkGameManually(unmatched, selection)
            
            val currentState = _uiState.value
            if (currentState is SyncUiState.Success) {
                val updatedList = currentState.unmatchedGames.filter { it != unmatched }
                _uiState.value = currentState.copy(unmatchedGames = updatedList)
            }
        }
    }

    fun searchCustomCandidates(unmatched: UnmatchedGame, query: String) {
        viewModelScope.launch {
            try {
                val newCandidates = gameRepository.searchGames(query)
                val currentState = _uiState.value
                if (currentState is SyncUiState.Success) {
                    val updatedList = currentState.unmatchedGames.map { 
                        if (it == unmatched) it.copy(candidates = newCandidates.take(10)) else it 
                    }
                    _uiState.value = currentState.copy(unmatchedGames = updatedList)
                }
            } catch (e: Exception) {
                println("Custom Search Error: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = SyncUiState.Idle
    }
}

sealed class SyncUiState {
    object Idle : SyncUiState()
    data class Loading(val progress: Float, val message: String) : SyncUiState()
    data class Success(
        val message: String, 
        val unmatchedGames: List<UnmatchedGame> = emptyList()
    ) : SyncUiState()
    data class Error(val message: String) : SyncUiState()
}
