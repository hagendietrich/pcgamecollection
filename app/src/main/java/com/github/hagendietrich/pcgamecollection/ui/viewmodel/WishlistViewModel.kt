package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.data.model.WishlistGame
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WishlistViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    val wishlistGames: StateFlow<List<WishlistGame>> = gameRepository.getAllWishlistGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- IGDB search state (same pattern as AddGameViewModel) ----
    private val _searchUiState = MutableStateFlow<WishlistSearchUiState>(WishlistSearchUiState.Idle)
    val searchUiState: StateFlow<WishlistSearchUiState> = _searchUiState.asStateFlow()

    /** True while store prices are being fetched after the user picked a game. */
    private val _isAdding = MutableStateFlow(false)
    val isAdding: StateFlow<Boolean> = _isAdding.asStateFlow()

    /** IDs of games whose prices are currently being refreshed. */
    private val _refreshingIds = MutableStateFlow<Set<Int>>(emptySet())
    val refreshingIds: StateFlow<Set<Int>> = _refreshingIds.asStateFlow()

    /** Stores only the game name for snackbar messages (allows UI translation). */
    private val _snackbarMessage = MutableStateFlow<Pair<Int, String?>?>(null)
    val snackbarMessage: StateFlow<Pair<Int, String?>?> = _snackbarMessage.asStateFlow()

    fun searchGames(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _searchUiState.value = WishlistSearchUiState.Loading
            try {
                val results = gameRepository.searchGames(query)
                _searchUiState.value =
                    if (results.isEmpty()) WishlistSearchUiState.Empty
                    else WishlistSearchUiState.Results(results)
            } catch (e: Exception) {
                _searchUiState.value = WishlistSearchUiState.Error("Search failed: ${e.message}")
            }
        }
    }

    /**
     * Adds a game to the wishlist: builds all available platform entries and fetches
     * current EUR prices from Steam/GOG before persisting.
     */
    fun addGame(igdbGame: IgdbGame) {
        viewModelScope.launch {
            _searchUiState.value = WishlistSearchUiState.Idle
            _isAdding.value = true
            try {
                if (igdbGame.id in wishlistGames.value.mapNotNull { it.igdbId }) {
                    _snackbarMessage.value = R.string.wishlist_already_present to igdbGame.name
                    return@launch
                }
                val entry = gameRepository.createWishlistEntryFromIgdb(igdbGame)
                gameRepository.addWishlistGame(entry)
                _snackbarMessage.value = R.string.wishlist_add_success to entry.title
            } catch (e: Exception) {
                e.printStackTrace()
                _snackbarMessage.value = R.string.wishlist_add_failed to null
            } finally {
                _isAdding.value = false
            }
        }
    }

    fun deleteGame(game: WishlistGame) {
        viewModelScope.launch {
            gameRepository.deleteWishlistGame(game)
        }
    }

    fun refreshPrices(game: WishlistGame) {
        viewModelScope.launch {
            _refreshingIds.value += game.id
            try {
                gameRepository.updateWishlistGame(gameRepository.refreshWishlistPrices(game))
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _refreshingIds.value -= game.id
            }
        }
    }

    fun refreshAllPrices() {
        viewModelScope.launch {
            val games = wishlistGames.value
            _refreshingIds.value = games.map { it.id }.toSet()
            try {
                games.forEach { game ->
                    gameRepository.updateWishlistGame(gameRepository.refreshWishlistPrices(game))
                    _refreshingIds.value -= game.id
                }
            } finally {
                _refreshingIds.value = emptySet()
            }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun clearSearchResults() {
        _searchUiState.value = WishlistSearchUiState.Idle
    }
}

sealed class WishlistSearchUiState {
    data object Idle : WishlistSearchUiState()
    data object Loading : WishlistSearchUiState()
    data object Empty : WishlistSearchUiState()
    data class Results(val games: List<IgdbGame>) : WishlistSearchUiState()
    data class Error(val message: String) : WishlistSearchUiState()
}
