package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.ContentResolver
import android.net.Uri
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.api.models.PlayniteGame
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.data.repository.UnmatchedGame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

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

    private val _playniteImportState = MutableStateFlow<PlayniteImportState>(PlayniteImportState.Idle)
    val playniteImportState: StateFlow<PlayniteImportState> = _playniteImportState.asStateFlow()

    private var tempPlayniteGames: List<PlayniteGame> = emptyList()

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
                val result = gameRepository.syncSteamGames(profileUrl) { progress, message ->
                    _uiState.value = ImportUiState.Loading(progress, message)
                }
                when {
                    result.importedCount == -1 -> {
                        _uiState.value = ImportUiState.Error("Steam API Key is missing. Please configure it in Settings.")
                    }
                    result.importedCount >= 0 -> {
                        val msg = if (result.importedCount > 0) 
                            "Successfully imported ${result.importedCount} games from Steam."
                        else "Steam library checked. No new games were added."
                        
                        _uiState.value = ImportUiState.Success(msg, result.unmatchedGames)
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
                val result = gameRepository.syncGogGames(username) { progress, message ->
                    _uiState.value = ImportUiState.Loading(progress, message)
                }
                if (result.importedCount >= 0) {
                    val msg = if (result.importedCount > 0)
                        "Successfully imported ${result.importedCount} games from GOG."
                    else "GOG library checked. No new games were added."
                    
                    _uiState.value = ImportUiState.Success(msg, result.unmatchedGames)
                } else {
                    _uiState.value = ImportUiState.Error("No games found. Ensure your profile is public.")
                }
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("GOG import failed: ${e.message}")
            }
        }
    }

    fun parsePlayniteJson(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading(0.2f, "Parsing Playnite JSON...")
            try {
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val games: List<PlayniteGame> = json.decodeFromString(jsonString)
                
                tempPlayniteGames = games
                val statuses = games.mapNotNull { it.completionStatus?.name }.distinct().sorted()
                
                _playniteImportState.value = PlayniteImportState.MappingRequired(statuses)
                _uiState.value = ImportUiState.Idle
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Failed to parse Playnite JSON: ${e.message}")
            }
        }
    }

    fun startPlayniteImport(mapping: Map<String, CompletionStatus>) {
        if (tempPlayniteGames.isEmpty()) return
        viewModelScope.launch {
            _playniteImportState.value = PlayniteImportState.Idle
            _uiState.value = ImportUiState.Loading(0f, "Starting Playnite import...")
            try {
                val result = gameRepository.syncPlayniteGames(tempPlayniteGames, mapping) { progress, message ->
                    _uiState.value = ImportUiState.Loading(progress, message)
                }
                
                val msg = if (result.importedCount > 0)
                    "Successfully imported ${result.importedCount} games from Playnite."
                else "Playnite library checked. No new games were added."
                
                _uiState.value = ImportUiState.Success(msg, result.unmatchedGames)
                tempPlayniteGames = emptyList()
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Playnite import failed: ${e.message}")
            }
        }
    }

    fun cancelPlayniteImport() {
        tempPlayniteGames = emptyList()
        _playniteImportState.value = PlayniteImportState.Idle
    }

    fun resolveUnmatchedGame(unmatched: UnmatchedGame, selection: IgdbGame) {
        viewModelScope.launch {
            gameRepository.linkGameManually(unmatched, selection)
            
            // Remove from the current list in UI
            val currentState = _uiState.value
            if (currentState is ImportUiState.Success) {
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
                if (currentState is ImportUiState.Success) {
                    val updatedList = currentState.unmatchedGames.map { 
                        if (it == unmatched) it.copy(candidates = newCandidates.take(10)) else it 
                    }
                    _uiState.value = currentState.copy(unmatchedGames = updatedList)
                }
            } catch (e: Exception) {
                // Silently fail or log, since it's a sub-search
                println("Custom Search Error: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = ImportUiState.Idle
    }

    fun exportToCsv(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading(0.5f, "Generating CSV file...")
            try {
                val csvContent = gameRepository.generateCsvContent()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(csvContent.toByteArray())
                }
                _uiState.value = ImportUiState.Success("Collection exported successfully!")
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Export failed: ${e.message}")
            }
        }
    }
}

sealed class ImportUiState {
    object Idle : ImportUiState()
    data class Loading(val progress: Float, val message: String) : ImportUiState()
    data class Success(
        val message: String, 
        val unmatchedGames: List<UnmatchedGame> = emptyList()
    ) : ImportUiState()
    data class Error(val message: String) : ImportUiState()
}

sealed class PlayniteImportState {
    object Idle : PlayniteImportState()
    data class MappingRequired(val uniqueStatuses: List<String>) : PlayniteImportState()
}
