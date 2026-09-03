package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameDetailViewModel(
    private val gameId: Int,
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GameDetailUiState>(GameDetailUiState.Loading)
    val uiState: StateFlow<GameDetailUiState> = _uiState.asStateFlow()

    private var enrichmentAttempted = false

    val allLabels: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.labels }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGenres: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.genres }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlatforms: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.platforms }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFranchises: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.franchises }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSeries: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.series }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _alternativeCovers = MutableStateFlow<List<String>>(emptyList())
    val alternativeCovers: StateFlow<List<String>> = _alternativeCovers.asStateFlow()

    private val _isFetchingCovers = MutableStateFlow(false)
    val isFetchingCovers: StateFlow<Boolean> = _isFetchingCovers.asStateFlow()

    private val _isSearchingReMatch = MutableStateFlow(false)
    val isSearchingReMatch: StateFlow<Boolean> = _isSearchingReMatch.asStateFlow()

    private val _reMatchResults = MutableStateFlow<List<IgdbGame>>(emptyList())
    val reMatchResults: StateFlow<List<IgdbGame>> = _reMatchResults.asStateFlow()

    init {
        loadGame()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun loadGame() {
        viewModelScope.launch {
            gameRepository.getGameByIdFlow(gameId).flatMapLatest { game ->
                if (game == null) {
                    flowOf(GameDetailUiState.Error("Game not found."))
                } else {
                    val igdbId = game.igdbId
                    val parentIgdbId = game.parentIgdbId
                    
                    val dlcsFlow = if (igdbId != null) gameRepository.getDlcForGame(igdbId) else flowOf(emptyList())
                    val wishlistDlcsFlow = if (igdbId != null) gameRepository.getWishlistDlcForGame(igdbId) else flowOf(emptyList())
                    
                    val bundleContentsFlow = if (igdbId != null) gameRepository.getGamesByBundleId(igdbId) else flowOf(emptyList())
                    val wishlistBundleContentsFlow = if (igdbId != null) gameRepository.getWishlistGamesByBundleId(igdbId) else flowOf(emptyList())
                    
                    val parentGameFlow = if (parentIgdbId != null) gameRepository.getGameByIgdbIdFlow(parentIgdbId) else flowOf(null)

                    combine(
                        dlcsFlow,
                        wishlistDlcsFlow,
                        bundleContentsFlow,
                        wishlistBundleContentsFlow,
                        parentGameFlow
                    ) { dlcs, wishlistDlcs, bundleContents, wishlistBundleContents, parentGame ->
                        val combinedRelated = (dlcs + bundleContents).distinctBy { it.igdbId ?: it.id }
                        val combinedWishlist = (wishlistDlcs + wishlistBundleContents).distinctBy { it.igdbId ?: it.id }
                        GameDetailUiState.Success(game, combinedRelated, combinedWishlist, parentGame)
                    }
                }
            }.collect { state ->
                _uiState.value = state
                
                if (state is GameDetailUiState.Success) {
                    val game = state.game
                    // Trigger automatic enrichment ONLY ONCE per screen session
                    if (!enrichmentAttempted) {
                        enrichmentAttempted = true
                        
                        val needsEnrichment = game.summary.isNullOrBlank() || 
                                              game.category == null ||
                                              (game.achievements.isEmpty() && (game.sourceIds.containsKey("STEAM") || game.sourceIds.containsKey("GOG") || game.sourceIds.containsKey("EPIC"))) ||
                                              ((game.playtimeSource == null || (game.playtimeSource == "HLTB" && game.hltbMain == game.hltbMainExtra)) && (game.category == 0 || game.category == 3 || game.category in 8..11))
                        
                        if (needsEnrichment) {
                            enrichGame()
                        }
                    }
                }
            }
        }
    }

    fun refreshMetadata() {
        viewModelScope.launch {
            try {
                gameRepository.refreshGameMetadata(gameId, force = true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun enrichGame() {
        viewModelScope.launch {
            try {
                gameRepository.refreshGameMetadata(gameId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateGame(game: Game) {
        viewModelScope.launch {
            gameRepository.updateGame(game)
        }
    }

    fun updatePersonalRating(rating: Int?) {
        viewModelScope.launch {
            val currentGame = (uiState.value as? GameDetailUiState.Success)?.game
            if (currentGame != null) {
                gameRepository.updateGame(currentGame.copy(personalRating = rating))
            } else {
                gameRepository.getGameByIdFlow(gameId).first()?.let {
                    gameRepository.updateGame(it.copy(personalRating = rating))
                }
            }
        }
    }

    fun deleteGame(game: Game, andIgnore: Boolean = false) {
        viewModelScope.launch {
            if (andIgnore) {
                gameRepository.deleteGameAndIgnore(game)
            } else {
                gameRepository.deleteGame(game)
            }
        }
    }

    fun searchForReMatch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _isSearchingReMatch.value = true
            try {
                _reMatchResults.value = gameRepository.searchGames(query)
            } finally {
                _isSearchingReMatch.value = false
            }
        }
    }

    fun applyReMatch(selection: IgdbGame) {
        viewModelScope.launch {
            gameRepository.reMatchGame(gameId, selection)
            _reMatchResults.value = emptyList()
        }
    }

    fun clearReMatchResults() {
        _reMatchResults.value = emptyList()
    }

    fun fetchAlternativeCovers() {
        viewModelScope.launch {
            _isFetchingCovers.value = true
            try {
                _alternativeCovers.value = gameRepository.fetchAlternativeCovers(gameId)
            } catch (e: Exception) {
                e.printStackTrace()
                _alternativeCovers.value = emptyList()
            } finally {
                _isFetchingCovers.value = false
            }
        }
    }

    fun clearAlternativeCovers() {
        _alternativeCovers.value = emptyList()
    }

    fun updateGameCover(imageUrl: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            gameRepository.updateGame(game.copy(
                coverImageUrl = imageUrl,
                isCoverManual = true
            ))
        }
    }

    fun updateReleaseDate(newDate: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            val normalized = gameRepository.normalizeDate(newDate) ?: newDate
            gameRepository.updateGame(game.copy(
                releaseDate = normalized,
                isReleaseDateManual = true
            ))
        }
    }

    fun updateGameModes(modes: List<String>) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            gameRepository.updateGame(game.copy(
                gameModes = gameRepository.sortGameModes(modes),
                isGameModeManual = true
            ))
        }
    }

    fun addLabelToGame(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (!game.labels.contains(label)) {
                gameRepository.updateGame(game.copy(labels = (game.labels + label).distinct()))
            }
        }
    }

    fun removeLabelFromGame(label: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (game.labels.contains(label)) {
                gameRepository.updateGame(game.copy(labels = game.labels - label))
            }
        }
    }

    fun addGenreToGame(genre: String) {
        if (genre.isBlank()) return
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (!game.genres.contains(genre)) {
                gameRepository.updateGame(game.copy(
                    genres = (game.genres + genre).distinct(),
                    isGenreManual = true
                ))
            }
        }
    }

    fun removeGenreFromGame(genre: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (game.genres.contains(genre)) {
                gameRepository.updateGame(game.copy(
                    genres = game.genres - genre,
                    isGenreManual = true
                ))
            }
        }
    }

    fun addFranchiseToGame(franchise: String) {
        if (franchise.isBlank()) return
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (!game.franchises.contains(franchise)) {
                gameRepository.updateGame(game.copy(
                    franchises = (game.franchises + franchise).distinct()
                ))
            }
        }
    }

    fun removeFranchiseFromGame(franchise: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (game.franchises.contains(franchise)) {
                gameRepository.updateGame(game.copy(
                    franchises = game.franchises - franchise
                ))
            }
        }
    }

    fun addSeriesToGame(seriesName: String) {
        if (seriesName.isBlank()) return
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (!game.series.contains(seriesName)) {
                gameRepository.updateGame(game.copy(
                    series = (game.series + seriesName).distinct()
                ))
            }
        }
    }

    fun removeSeriesFromGame(seriesName: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (game.series.contains(seriesName)) {
                gameRepository.updateGame(game.copy(
                    series = game.series - seriesName
                ))
            }
        }
    }

    fun addPlatformToGame(platform: String) {
        if (platform.isBlank()) return
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (!game.platforms.contains(platform)) {
                gameRepository.updateGame(game.copy(
                    platforms = (game.platforms + platform).distinct()
                ))
            }
        }
    }

    fun removePlatformFromGame(platform: String) {
        viewModelScope.launch {
            val game = (uiState.value as? GameDetailUiState.Success)?.game ?: return@launch
            if (game.platforms.contains(platform)) {
                gameRepository.updateGame(game.copy(
                    platforms = game.platforms - platform
                ))
            }
        }
    }
}

sealed class GameDetailUiState {
    object Loading : GameDetailUiState()
    data class Success(
        val game: Game,
        val dlcs: List<Game> = emptyList(),
        val wishlistDlcs: List<com.github.hagendietrich.pcgamecollection.data.model.WishlistGame> = emptyList(),
        val parentGame: Game? = null
    ) : GameDetailUiState()
    data class Error(val message: String) : GameDetailUiState()
}
