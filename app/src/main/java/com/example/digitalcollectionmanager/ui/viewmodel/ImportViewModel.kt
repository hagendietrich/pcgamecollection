package com.example.digitalcollectionmanager.ui.viewmodel

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.io.File
import java.io.FileInputStream

class ImportViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _playniteImportState = MutableStateFlow<PlayniteImportState>(PlayniteImportState.Idle)
    val playniteImportState: StateFlow<PlayniteImportState> = _playniteImportState.asStateFlow()

    private val _hasAllFilesAccess = MutableStateFlow(false)
    val hasAllFilesAccess: StateFlow<Boolean> = _hasAllFilesAccess.asStateFlow()

    private var tempPlayniteGames: List<PlayniteGame> = emptyList()

    init {
        checkPermissionStatus()
    }

    fun checkPermissionStatus() {
        _hasAllFilesAccess.value = Environment.isExternalStorageManager()
    }

    fun parsePlayniteJson(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Loading(0.2f, "Opening Playnite JSON...")
            try {
                val jsonString = readFileContent(uri, contentResolver)

                _uiState.value = ImportUiState.Loading(0.4f, "Parsing JSON...")
                val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
                val games: List<PlayniteGame> = json.decodeFromString(jsonString)
                
                tempPlayniteGames = games
                val statuses = games.mapNotNull { it.completionStatus?.name }.distinct().sorted()
                
                _playniteImportState.value = PlayniteImportState.MappingRequired(statuses)
                _uiState.value = ImportUiState.Idle
            } catch (e: Exception) {
                Log.e("ImportViewModel", "Playnite Parse Error", e)
                val errorMsg = "Playnite Parse Error (${e::class.simpleName}): ${e.message ?: "Unknown error"}"
                _uiState.value = ImportUiState.Error(errorMsg)
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
            _uiState.value = ImportUiState.Loading(0.3f, "Opening backup file...")
            try {
                val jsonString = readFileContent(uri, contentResolver)

                gameRepository.importFromJson(jsonString)
                _uiState.value = ImportUiState.Success("Library imported successfully!")
            } catch (e: Exception) {
                Log.e("ImportViewModel", "Import Error", e)
                val errorMsg = "Import Error (${e::class.simpleName}): ${e.message ?: "Unknown error"}"
                _uiState.value = ImportUiState.Error(errorMsg)
            }
        }
    }

    /**
     * Tiered file access strategy to bypass system crashes in containerized environments.
     */
    private fun readFileContent(uri: Uri, contentResolver: ContentResolver): String {
        Log.d("ImportViewModel", "Attempting tiered read for URI: $uri (Auth: ${uri.authority})")
        val isManager = Environment.isExternalStorageManager()
        Log.d("ImportViewModel", "All Files Access Status: $isManager")

        // Tier 0: Direct Path Fallback (System Bypass for Waydroid)
        if (uri.authority == "com.android.externalstorage.documents" || uri.authority == "com.android.providers.downloads.documents") {
            val docId = uri.pathSegments.lastOrNull() ?: ""
            val relativePath = when {
                docId.startsWith("primary:") -> docId.substringAfter("primary:")
                docId.startsWith("raw:") -> docId.substringAfter("raw:")
                else -> null
            }
            
            if (relativePath != null) {
                val candidates = listOf(
                    File("/storage/emulated/0/$relativePath"),
                    File("/sdcard/$relativePath"),
                    File(Environment.getExternalStorageDirectory(), relativePath)
                ).distinctBy { it.absolutePath }
                
                for (file in candidates) {
                    Log.d("ImportViewModel", "Tier 0: Checking ${file.absolutePath}")
                    try {
                        if (file.exists()) {
                            val content = file.readText()
                            Log.d("ImportViewModel", "Tier 0: SUCCESS via direct read")
                            return content
                        } else {
                            Log.d("ImportViewModel", "Tier 0: File does not exist: ${file.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.w("ImportViewModel", "Tier 0: Candidate ${file.absolutePath} failed: ${e.message}")
                    }
                }
            }
        }

        // Tier 1: openAssetFileDescriptor
        try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                Log.d("ImportViewModel", "Read Success via Tier 1 (AssetFileDescriptor)")
                return FileInputStream(afd.fileDescriptor).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.w("ImportViewModel", "Tier 1 Failed: ${e.message}")
            if (e is NullPointerException) handleSystemNpe(e)
        }

        // Tier 2: acquireContentProviderClient
        try {
            contentResolver.acquireContentProviderClient(uri)?.use { client ->
                client.openFile(uri, "r")?.use { pfd ->
                    Log.d("ImportViewModel", "Read Success via Tier 2 (Direct Client)")
                    return FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
                }
            }
        } catch (e: Exception) {
            Log.w("ImportViewModel", "Tier 2 Failed: ${e.message}")
            if (e is NullPointerException) handleSystemNpe(e)
        }

        // Tier 3: Standard openFileDescriptor
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                Log.d("ImportViewModel", "Read Success via Tier 3 (FileDescriptor)")
                return FileInputStream(pfd.fileDescriptor).bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.w("ImportViewModel", "Tier 3 Failed: ${e.message}")
            if (e is NullPointerException) handleSystemNpe(e)
            throw e
        }

        throw Exception("All file access methods failed. On Waydroid, please ensure 'All Files Access' is granted in settings to enable the system bypass.")
    }

    /**
     * Specifically handles the system-level NPE seen on Waydroid to provide a helpful error message.
     */
    private fun handleSystemNpe(e: NullPointerException): Nothing {
        throw Exception("Waydroid System Error (NPE): The system container crashed while opening the file. Please grant 'All Files Access' in settings to enable the direct bypass.", e)
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
