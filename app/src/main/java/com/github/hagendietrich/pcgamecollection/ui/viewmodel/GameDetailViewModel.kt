package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GameDetailViewModel(
    private val gameId: Int,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GameDetailUiState>(GameDetailUiState.Loading)
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    init {
        loadGame()
    }

    private fun loadGame() {
        viewModelScope.launch {
            gameRepository.getAllGames().collect { games ->
                val game = games.find { it.id == gameId }
                if (game != null) {
                    // Start enrichment if missing metadata (aligned with repo logic)
                    val needsEnrichment = game.summary.isNullOrBlank() || 
                                          game.screenshotUrls.isEmpty() || 
                                          game.developers.isEmpty() ||
                                          game.storeUrls.isEmpty()
                    
                    if (needsEnrichment) {
                        enrichGame()
                    }
                    _uiState.value = GameDetailUiState.Success(game)
                } else {
                    _uiState.value = GameDetailUiState.Error("Game not found.")
                }
            }
        }
    }

    private fun enrichGame() {
        viewModelScope.launch {
            try {
                gameRepository.refreshGameMetadata(gameId)
            } catch (e: Exception) {
                // Silently fail or log, since this is an enhancement
                e.printStackTrace()
            }
        }
    }
}

sealed class GameDetailUiState {
    object Loading : GameDetailUiState()
    data class Success(val game: Game) : GameDetailUiState()
    data class Error(val message: String) : GameDetailUiState()
}
