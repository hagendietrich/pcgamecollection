package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.GameSource
import com.example.digitalcollectionmanager.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AddGameViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddGameUiState>(AddGameUiState.Idle)
    val uiState: StateFlow<AddGameUiState> = _uiState.asStateFlow()

    private val _addedGameIds = MutableStateFlow<Set<Long>>(emptySet())
    val addedGameIds: StateFlow<Set<Long>> = _addedGameIds.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun searchGames(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch {
            _uiState.value = AddGameUiState.Loading
            try {
                val results = gameRepository.searchGames(query)
                if (results.isEmpty()) {
                    _uiState.value = AddGameUiState.Empty
                } else {
                    // Check which games are already in the library
                    val existingIds = mutableSetOf<Long>()
                    results.forEach { igdbGame ->
                        val existing = gameRepository.getGameByExternalId(igdbGame.id, GameSource.IGDB.name)
                        if (existing != null) {
                            existingIds.add(igdbGame.id)
                        }
                    }
                    _addedGameIds.value = existingIds
                    _uiState.value = AddGameUiState.Results(results)
                }
            } catch (e: Exception) {
                _uiState.value = AddGameUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    fun addGame(igdbGame: IgdbGame) {
        viewModelScope.launch {
            val game = Game(
                title = igdbGame.name,
                platform = "IGDB",
                coverImageUrl = igdbGame.cover?.url?.let { gameRepository.getFullCoverUrl(it) },
                releaseDate = igdbGame.firstReleaseDate?.let { formatTimestamp(it) },
                isOwned = true,
                externalId = igdbGame.id,
                source = GameSource.IGDB.name
            )
            gameRepository.addGame(game)
            
            // Update tracking state
            _addedGameIds.value += igdbGame.id
            _snackbarMessage.value = "Added ${igdbGame.name} to library"
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    private fun formatTimestamp(timestamp: Long): String {
        val date = Date(timestamp * 1000L)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(date)
    }
}

sealed class AddGameUiState {
    object Idle : AddGameUiState()
    object Loading : AddGameUiState()
    object Empty : AddGameUiState()
    data class Results(val games: List<IgdbGame>) : AddGameUiState()
    data class Error(val message: String) : AddGameUiState()
}
