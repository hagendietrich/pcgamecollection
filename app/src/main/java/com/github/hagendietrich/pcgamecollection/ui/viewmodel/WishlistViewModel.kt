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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class WishlistSortOrder {
    RELEASE_DATE_DESC,
    RELEASE_DATE_ASC,
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    BEST_PRICE
}

class WishlistViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _sortOrder = MutableStateFlow(WishlistSortOrder.DATE_ADDED_DESC)
    val sortOrder: StateFlow<WishlistSortOrder> = _sortOrder.asStateFlow()

    val wishlistGames: StateFlow<List<WishlistGame>> = gameRepository.getAllWishlistGames()
        .combine(_sortOrder) { games, sortOrder ->
            when (sortOrder) {
                WishlistSortOrder.RELEASE_DATE_DESC -> games.sortedByDescending { it.releaseDate ?: 0L }
                WishlistSortOrder.RELEASE_DATE_ASC -> games.sortedBy { it.releaseDate ?: Long.MAX_VALUE }
                WishlistSortOrder.DATE_ADDED_DESC -> games.sortedByDescending { it.dateAdded }
                WishlistSortOrder.DATE_ADDED_ASC -> games.sortedBy { it.dateAdded }
                WishlistSortOrder.BEST_PRICE -> games.sortedBy { it.lowestPrice()?.price ?: Double.MAX_VALUE }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryIgdbIds: StateFlow<Set<Long>> = gameRepository.getAllGames()
        .combine(MutableStateFlow(Unit)) { games, _ -> games.mapNotNull { it.igdbId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setSortOrder(order: WishlistSortOrder) {
        _sortOrder.value = order
    }

    // ---- IGDB search state (same pattern as AddGameViewModel) ----
    private val _searchUiState = MutableStateFlow<WishlistSearchUiState>(WishlistSearchUiState.Idle)
    val searchUiState: StateFlow<WishlistSearchUiState> = _searchUiState.asStateFlow()

    /** True while store prices are being fetched after the user picked a game. */
    private val _isAdding = MutableStateFlow(false)
    val isAdding: StateFlow<Boolean> = _isAdding.asStateFlow()

    private val _selectedGameForDetail = MutableStateFlow<IgdbGame?>(null)
    val selectedGameForDetail: StateFlow<IgdbGame?> = _selectedGameForDetail.asStateFlow()

    private val gameDetailCache = mutableMapOf<Long, IgdbGame>()

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

    fun selectGameForDetail(igdbId: Long?) {
        if (igdbId == null) {
            _selectedGameForDetail.value = null
            return
        }
        
        // Return cached value immediately if available
        val cached = gameDetailCache[igdbId]
        if (cached != null) {
            _selectedGameForDetail.value = cached
            return
        }

        viewModelScope.launch {
            val game = gameRepository.getIgdbGame(igdbId)
            if (game != null) {
                gameDetailCache[igdbId] = game
                _selectedGameForDetail.value = game
            }
        }
    }

    fun addGameToLibrary(igdbGame: IgdbGame) {
        viewModelScope.launch {
            val game = com.github.hagendietrich.pcgamecollection.data.model.Game(
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
            _snackbarMessage.value = R.string.add_game_success to igdbGame.name
            
            // Optionally remove from wishlist if added to library
            val wishlistEntry = wishlistGames.value.find { it.igdbId == igdbGame.id }
            if (wishlistEntry != null) {
                gameRepository.deleteWishlistGame(wishlistEntry)
            }
        }
    }
}

sealed class WishlistSearchUiState {
    data object Idle : WishlistSearchUiState()
    data object Loading : WishlistSearchUiState()
    data object Empty : WishlistSearchUiState()
    data class Results(val games: List<IgdbGame>) : WishlistSearchUiState()
    data class Error(val message: String) : WishlistSearchUiState()
}
