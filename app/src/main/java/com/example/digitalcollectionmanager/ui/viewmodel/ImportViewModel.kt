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
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ImportViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _playniteImportState = MutableStateFlow<PlayniteImportState>(PlayniteImportState.Idle)
    val playniteImportState: StateFlow<PlayniteImportState> = _playniteImportState.asStateFlow()

    private var tempPlayniteGames: List<PlayniteGame> = emptyList()

    init {
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
                
                val msg = StringBuilder("Successfully imported ${result.importedCount} games from Playnite.")
                if (result.alreadyPresentCount > 0) {
                    msg.append(" ${result.alreadyPresentCount} games were already in your library.")
                }
                if (result.ignoredCount > 0) {
                    msg.append(" ${result.ignoredCount} games were ignored.")
                }
                
                _uiState.value = ImportUiState.Success(msg.toString(), result.unmatchedGames)
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

    fun ignoreUnmatchedGame(unmatched: UnmatchedGame) {
        viewModelScope.launch {
            gameRepository.ignoreGame(unmatched.storeTitle, unmatched.platform, unmatched.storeId)
            
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

    fun exportToJson(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading(0.5f, "Generating JSON backup...")
            try {
                val jsonContent = gameRepository.generateJsonContent()
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                }
                _uiState.value = ImportUiState.Success("Library exported successfully!")
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Export failed: ${e.message}")
            }
        }
    }

    fun importFromJson(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading(0.3f, "Reading JSON file...")
            try {
                val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
                gameRepository.importFromJson(jsonString)
                _uiState.value = ImportUiState.Success("Library imported successfully!")
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Import failed: ${e.message}")
            }
        }
    }

    fun wipeLibrary() {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading(0.5f, "Wiping library...")
            try {
                gameRepository.clearLibrary()
                _uiState.value = ImportUiState.Success("Library has been cleared.")
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Failed to clear library: ${e.message}")
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
