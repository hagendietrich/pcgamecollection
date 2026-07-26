package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AddGameViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddGameUiState>(AddGameUiState.Idle)
    val uiState: StateFlow<AddGameUiState> = _uiState.asStateFlow()

    private val _addedGameIds = MutableStateFlow<Set<Long>>(emptySet())
    val addedGameIds: StateFlow<Set<Long>> = _addedGameIds.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    /** Stores only the game name for success messages to allow UI translation */
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
                    // Check which games are already in the library using IGDB ID
                    val existingIds = mutableSetOf<Long>()
                    results.forEach { igdbGame ->
                        val existing = gameRepository.getGameByIgdbId(igdbGame.id)
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
                platforms = listOf("IGDB"),
                coverImageUrl = igdbGame.cover?.url?.let { gameRepository.getFullCoverUrl(it) },
                releaseDate = gameRepository.formatTimestamp(igdbGame.firstReleaseDate),
                isOwned = true,
                igdbId = igdbGame.id,
                sourceIds = mapOf("IGDB" to igdbGame.id.toString()),
                genres = igdbGame.genres?.map { it.name } ?: emptyList()
            )
            gameRepository.addGame(game)
            
            // Update tracking state
            _addedGameIds.value += igdbGame.id
            _snackbarMessage.value = igdbGame.name
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
}

sealed class AddGameUiState {
    object Idle : AddGameUiState()
    object Loading : AddGameUiState()
    object Empty : AddGameUiState()
    data class Results(val games: List<IgdbGame>) : AddGameUiState()
    data class Error(val message: String) : AddGameUiState()
}
