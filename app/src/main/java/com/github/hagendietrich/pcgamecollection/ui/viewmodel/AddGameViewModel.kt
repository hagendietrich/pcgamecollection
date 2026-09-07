package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
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

    private val _wishlistGameIds = MutableStateFlow<Set<Long>>(emptySet())
    val wishlistGameIds: StateFlow<Set<Long>> = _wishlistGameIds.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    /** Stores only the game name for success messages to allow UI translation */
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _selectedGameForDetail = MutableStateFlow<IgdbGame?>(null)
    val selectedGameForDetail: StateFlow<IgdbGame?> = _selectedGameForDetail.asStateFlow()

    init {
        fetchAnticipatedGames()
    }

    fun fetchAnticipatedGames() {
        viewModelScope.launch {
            _uiState.value = AddGameUiState.Loading
            try {
                val results = gameRepository.getTopAnticipatedGames()
                if (results.isEmpty()) {
                    _uiState.value = AddGameUiState.Idle
                } else {
                    updateTrackedGameIds(results)
                    _uiState.value = AddGameUiState.AnticipatedResults(results)
                }
            } catch (e: Exception) {
                _uiState.value = AddGameUiState.Error("Failed to fetch anticipated games: ${e.message}")
            }
        }
    }

    private suspend fun updateTrackedGameIds(games: List<IgdbGame>) {
        val existingIds = mutableSetOf<Long>()
        val wishlistIds = mutableSetOf<Long>()
        games.forEach { igdbGame ->
            if (gameRepository.getGameByIgdbId(igdbGame.id) != null) {
                existingIds.add(igdbGame.id)
            }
            if (gameRepository.getWishlistGameByIgdbId(igdbGame.id) != null) {
                wishlistIds.add(igdbGame.id)
            }
        }
        _addedGameIds.value = existingIds
        _wishlistGameIds.value = wishlistIds
    }

    fun searchGames(query: String) {
        if (query.isBlank()) {
            fetchAnticipatedGames()
            return
        }

        viewModelScope.launch {
            _uiState.value = AddGameUiState.Loading
            try {
                val results = gameRepository.searchGames(query)
                if (results.isEmpty()) {
                    _uiState.value = AddGameUiState.Empty
                } else {
                    updateTrackedGameIds(results)
                    _uiState.value = AddGameUiState.Results(results)
                }
            } catch (e: Exception) {
                _uiState.value = AddGameUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    fun selectGameForDetail(game: IgdbGame?) {
        _selectedGameForDetail.value = game
    }

    fun addGame(igdbGame: IgdbGame) {
        viewModelScope.launch {
            val game = Game(
                title = igdbGame.name,
                platforms = listOf("IGDB"),
                coverImageUrl = igdbGame.cover?.url?.let { gameRepository.getFullCoverUrl(it) },
                releaseDate = gameRepository.normalizeDate(gameRepository.formatTimestamp(igdbGame.firstReleaseDate)),
                isOwned = true,
                igdbId = igdbGame.id,
                sourceIds = mapOf("IGDB" to igdbGame.id.toString()),
                genres = igdbGame.genres?.map { it.name } ?: emptyList(),
                summary = igdbGame.summary,
                screenshotUrls = igdbGame.screenshots?.map { gameRepository.getFullScreenshotUrl(it.url) } ?: emptyList(),
                igdbUrl = igdbGame.url,
                userRating = igdbGame.rating,
                criticRating = igdbGame.aggregatedRating,
                developers = igdbGame.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList(),
                publishers = igdbGame.involvedCompanies?.filter { it.publisher }?.mapNotNull { it.company?.name } ?: emptyList(),
                themes = igdbGame.themes?.map { it.name } ?: emptyList(),
                keywords = igdbGame.keywords?.map { it.name } ?: emptyList(),
                franchises = igdbGame.franchises?.map { it.name } ?: emptyList(),
                series = (listOfNotNull(igdbGame.collection?.name) + (igdbGame.collections?.mapNotNull { it.name } ?: emptyList())).distinct(),
                gameModes = gameRepository.mapIgdbGameModes(igdbGame.gameModes),
                storeUrls = gameRepository.extractStoreUrls(igdbGame, mapOf("IGDB" to igdbGame.id.toString())),
                parentIgdbId = igdbGame.parentGame,
                category = igdbGame.category,
                bundleIgdbIds = igdbGame.bundles ?: emptyList()
            )
            gameRepository.addGame(game)
            
            // Update tracking state
            _addedGameIds.value += igdbGame.id
            _snackbarMessage.value = igdbGame.name
        }
    }

    fun addToWishlist(igdbGame: IgdbGame) {
        viewModelScope.launch {
            val wishlistGame = gameRepository.createWishlistEntryFromIgdb(igdbGame)
            gameRepository.addWishlistGame(wishlistGame)
            
            _wishlistGameIds.value += igdbGame.id
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
    data class AnticipatedResults(val games: List<IgdbGame>) : AddGameUiState()
    data class Error(val message: String) : AddGameUiState()
}
