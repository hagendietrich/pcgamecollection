package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.model.IgnoredGame
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.data.repository.UnmatchedGame
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
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

    private val _epicEmail = MutableStateFlow("")
    val epicEmail: StateFlow<String> = _epicEmail.asStateFlow()

    private val _epicPassword = MutableStateFlow("")
    val epicPassword: StateFlow<String> = _epicPassword.asStateFlow()

    private val _showEpicLogin = MutableStateFlow(false)
    val showEpicLogin: StateFlow<Boolean> = _showEpicLogin.asStateFlow()

    private val _showUbisoftLogin = MutableStateFlow(false)
    val showUbisoftLogin: StateFlow<Boolean> = _showUbisoftLogin.asStateFlow()

    private val _showBattleNetLogin = MutableStateFlow(false)
    val showBattleNetLogin: StateFlow<Boolean> = _showBattleNetLogin.asStateFlow()

    val ignoredGames: StateFlow<List<IgnoredGame>> = gameRepository.getIgnoredGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            settingsRepository.lastSteamId.firstOrNull()?.let { _lastSteamId.value = it }
            settingsRepository.lastGogUsername.firstOrNull()?.let { _lastGogUsername.value = it }
            settingsRepository.epicEmail.firstOrNull()?.let { _epicEmail.value = it }
            settingsRepository.getEpicPassword()?.let { _epicPassword.value = it }
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
                        val msg = StringBuilder("Successfully synced ${result.importedCount} games from Steam.")
                        if (result.alreadyPresentCount > 0) {
                            msg.append(" ${result.alreadyPresentCount} games were already in your library.")
                        }
                        if (result.ignoredCount > 0) {
                            msg.append(" ${result.ignoredCount} games were ignored.")
                        }
                        
                        _uiState.value = SyncUiState.Success(msg.toString(), result.unmatchedGames)
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
                    val msg = StringBuilder("Successfully synced ${result.importedCount} games from GOG.")
                    if (result.alreadyPresentCount > 0) {
                        msg.append(" ${result.alreadyPresentCount} games were already in your library.")
                    }
                    if (result.ignoredCount > 0) {
                        msg.append(" ${result.ignoredCount} games were ignored.")
                    }
                    
                    _uiState.value = SyncUiState.Success(msg.toString(), result.unmatchedGames)
                } else {
                    _uiState.value = SyncUiState.Error("No games found. Ensure your profile is public.")
                }
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("GOG sync failed: ${e.message}")
            }
        }
    }

    fun setShowEpicLogin(show: Boolean) {
        _showEpicLogin.value = show
    }

    fun updateEpicCredentials(email: String, password: String) {
        val trimmedEmail = email.trim()
        val trimmedPassword = password.trim()
        _epicEmail.value = trimmedEmail
        _epicPassword.value = trimmedPassword
        viewModelScope.launch {
            settingsRepository.saveEpicCredentials(trimmedEmail, trimmedPassword)
        }
    }

    fun setShowUbisoftLogin(show: Boolean) {
        _showUbisoftLogin.value = show
    }

    fun setShowBattleNetLogin(show: Boolean) {
        _showBattleNetLogin.value = show
    }

    fun onUbisoftSessionCaptured(ticket: String, sessionId: String) {
        _showUbisoftLogin.value = false
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.value = SyncUiState.Loading(0f, "Initializing Ubisoft Connect sync...")
            try {
                val result = gameRepository.syncUbisoftGames(ticket, sessionId) { progress, message ->
                    _uiState.value = SyncUiState.Loading(progress, message)
                }
                val msg = StringBuilder("Successfully synced ${result.importedCount} games from Ubisoft Connect.")
                if (result.alreadyPresentCount > 0) {
                    msg.append(" ${result.alreadyPresentCount} games were already in your library.")
                }
                if (result.ignoredCount > 0) {
                    msg.append(" ${result.ignoredCount} games were ignored.")
                }
                
                _uiState.value = SyncUiState.Success(msg.toString(), result.unmatchedGames)
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("Ubisoft sync failed: ${e.message}")
            }
        }
    }

    fun onBattleNetCookiesCaptured(cookies: String) {
        _showBattleNetLogin.value = false
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.value = SyncUiState.Loading(0f, "Initializing Battle.net sync...")
            try {
                val result = gameRepository.syncBattleNetGames(cookies) { progress, message ->
                    _uiState.value = SyncUiState.Loading(progress, message)
                }
                val msg = StringBuilder("Successfully synced ${result.importedCount} games from Battle.net.")
                if (result.alreadyPresentCount > 0) {
                    msg.append(" ${result.alreadyPresentCount} games were already in your library.")
                }
                if (result.ignoredCount > 0) {
                    msg.append(" ${result.ignoredCount} games were ignored.")
                }

                _uiState.value = SyncUiState.Success(msg.toString(), result.unmatchedGames)
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("Battle.net sync failed: ${e.message}")
            }
        }
    }

    fun onEpicCodeCaptured(code: String) {
        _showEpicLogin.value = false
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            _uiState.value = SyncUiState.Loading(0f, "Initializing Epic Games sync...")
            try {
                val result = gameRepository.syncEpicGames(code) { progress, message ->
                    _uiState.value = SyncUiState.Loading(progress, message)
                }
                val msg = StringBuilder("Successfully synced ${result.importedCount} games from Epic Games Store.")
                if (result.alreadyPresentCount > 0) {
                    msg.append(" ${result.alreadyPresentCount} games were already in your library.")
                }
                if (result.ignoredCount > 0) {
                    msg.append(" ${result.ignoredCount} games were ignored.")
                }
                
                _uiState.value = SyncUiState.Success(msg.toString(), result.unmatchedGames)
            } catch (e: Exception) {
                _uiState.value = SyncUiState.Error("Epic sync failed: ${e.message}")
            }
        }
    }

    fun syncAllAccounts() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val steamId = _lastSteamId.value
            val gogUser = _lastGogUsername.value
            
            if (steamId.isBlank() && gogUser.isBlank()) {
                _uiState.value = SyncUiState.Error("No accounts connected. Please provide credentials.")
                return@launch
            }

            var totalImported = 0
            var totalAlreadyPresent = 0
            var totalIgnored = 0
            val allUnmatched = mutableListOf<UnmatchedGame>()
            
            try {
                // Step 1: Steam
                if (steamId.isNotBlank()) {
                    _uiState.value = SyncUiState.Loading(0f, "Starting Steam sync...")
                    val steamResult = gameRepository.syncSteamGames(steamId) { progress, message ->
                        _uiState.value = SyncUiState.Loading(progress * 0.5f, "Steam: $message")
                    }
                    if (steamResult.importedCount >= 0) {
                        totalImported += steamResult.importedCount
                        totalAlreadyPresent += steamResult.alreadyPresentCount
                        totalIgnored += steamResult.ignoredCount
                    }
                    allUnmatched.addAll(steamResult.unmatchedGames)
                }

                // Step 2: GOG
                if (gogUser.isNotBlank()) {
                    _uiState.value = SyncUiState.Loading(0.5f, "Starting GOG sync...")
                    val gogResult = gameRepository.syncGogGames(gogUser) { progress, message ->
                        _uiState.value = SyncUiState.Loading(0.5f + (progress * 0.5f), "GOG: $message")
                    }
                    if (gogResult.importedCount >= 0) {
                        totalImported += gogResult.importedCount
                        totalAlreadyPresent += gogResult.alreadyPresentCount
                        totalIgnored += gogResult.ignoredCount
                    }
                    allUnmatched.addAll(gogResult.unmatchedGames)
                }

                val msg = StringBuilder("Sync All complete. Total new games: $totalImported.")
                if (totalAlreadyPresent > 0) {
                    msg.append(" $totalAlreadyPresent games were already in your library.")
                }
                if (totalIgnored > 0) {
                    msg.append(" $totalIgnored games were ignored.")
                }
                _uiState.value = SyncUiState.Success(msg.toString(), allUnmatched)

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

    fun ignoreUnmatchedGame(unmatched: UnmatchedGame) {
        viewModelScope.launch {
            gameRepository.ignoreGame(unmatched.storeTitle, unmatched.platform, unmatched.storeId)
            
            val currentState = _uiState.value
            if (currentState is SyncUiState.Success) {
                val updatedList = currentState.unmatchedGames.filter { it != unmatched }
                _uiState.value = currentState.copy(unmatchedGames = updatedList)
            }
        }
    }

    fun removeIgnoredGame(id: Int) {
        viewModelScope.launch {
            gameRepository.removeIgnoredGame(id)
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
