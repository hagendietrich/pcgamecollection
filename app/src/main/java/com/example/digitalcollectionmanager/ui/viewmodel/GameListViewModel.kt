package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.GroupingType
import com.example.digitalcollectionmanager.data.model.SortOrder
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameListViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _selectedGameIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedGameIds: StateFlow<Set<Int>> = _selectedGameIds.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val allGames: StateFlow<List<Game>> = gameRepository.getAllGames()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isMultiSelectMode: StateFlow<Boolean> = _selectedGameIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val groupingType: StateFlow<GroupingType> = settingsRepository.groupingType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupingType.NONE)

    val groupedGames: StateFlow<Map<String, List<Game>>> = combine(
        gameRepository.getAllGames(),
        settingsRepository.sortOrder,
        settingsRepository.groupingType,
        _searchQuery
    ) { games, sortOrder, groupingType, query ->
        val filtered = if (query.isBlank()) {
            games
        } else {
            games.filter { it.title.contains(query, ignoreCase = true) }
        }

        val sorted = when (sortOrder) {
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.RELEASE_DATE_ASC -> filtered.sortedBy { gameRepository.normalizeDate(it.releaseDate) ?: "" }
            SortOrder.RELEASE_DATE_DESC -> filtered.sortedByDescending { gameRepository.normalizeDate(it.releaseDate) ?: "" }
            SortOrder.PLAYTIME_ASC -> filtered.sortedBy { it.playtimeMinutes }
            SortOrder.PLAYTIME_DESC -> filtered.sortedByDescending { it.playtimeMinutes }
        }

        when (groupingType) {
            GroupingType.NONE -> mapOf("" to sorted)
            GroupingType.STATUS -> sorted.groupBy { it.completionStatus.name }
                .toSortedMap(compareBy { statusName ->
                    try { CompletionStatus.valueOf(statusName).priority } catch (e: Exception) { 99 }
                })
            GroupingType.LABEL -> {
                val labeled = mutableMapOf<String, MutableList<Game>>()
                sorted.forEach { game ->
                    if (game.labels.isEmpty()) {
                        labeled.getOrPut("Unlabeled") { mutableListOf() }.add(game)
                    } else {
                        game.labels.forEach { label ->
                            labeled.getOrPut(label) { mutableListOf() }.add(game)
                        }
                    }
                }
                labeled
            }
            GroupingType.PLATFORM -> {
                val platformMap = mutableMapOf<String, MutableList<Game>>()
                sorted.forEach { game ->
                    if (game.platforms.isEmpty()) {
                        platformMap.getOrPut("Other") { mutableListOf() }.add(game)
                    } else {
                        game.platforms.forEach { platform ->
                            platformMap.getOrPut(platform) { mutableListOf() }.add(game)
                        }
                    }
                }
                platformMap.toSortedMap()
            }
            GroupingType.GENRE -> {
                val genreMap = mutableMapOf<String, MutableList<Game>>()
                sorted.forEach { game ->
                    if (game.genres.isEmpty()) {
                        genreMap.getOrPut("Unknown Genre") { mutableListOf() }.add(game)
                    } else {
                        game.genres.forEach { genre ->
                            genreMap.getOrPut(genre) { mutableListOf() }.add(game)
                        }
                    }
                }
                genreMap.toSortedMap()
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val columnCount: StateFlow<Int> = settingsRepository.columnCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val sortOrder: StateFlow<SortOrder> = settingsRepository.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.TITLE_ASC)

    val allLabels: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.labels }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGenres: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.genres }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _reMatchResults = MutableStateFlow<List<IgdbGame>>(emptyList())
    val reMatchResults: StateFlow<List<IgdbGame>> = _reMatchResults.asStateFlow()

    fun searchForReMatch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _reMatchResults.value = gameRepository.searchGames(query)
        }
    }

    fun applyReMatch(gameId: Int, selection: IgdbGame) {
        viewModelScope.launch {
            gameRepository.reMatchGame(gameId, selection)
            _reMatchResults.value = emptyList()
        }
    }

    fun clearReMatchResults() {
        _reMatchResults.value = emptyList()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setColumnCount(count: Int) {
        viewModelScope.launch {
            settingsRepository.updateColumnCount(count)
        }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch {
            settingsRepository.updateSortOrder(order)
        }
    }

    fun setGroupingType(type: GroupingType) {
        viewModelScope.launch {
            settingsRepository.updateGroupingType(type)
        }
    }

    fun toggleSelection(gameId: Int) {
        if (_selectedGameIds.value.contains(gameId)) {
            _selectedGameIds.value -= gameId
        } else {
            _selectedGameIds.value += gameId
        }
    }

    fun clearSelection() {
        _selectedGameIds.value = emptySet()
    }

    fun updateSelectedStatus(status: CompletionStatus) {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            gameRepository.getAllGames().first().filter { it.id in selectedIds }.forEach { game ->
                gameRepository.updateGame(game.copy(completionStatus = status))
            }
            clearSelection()
        }
    }

    fun addLabelToSelected(label: String) {
        if (label.isBlank()) return
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            gameRepository.getAllGames().first().filter { it.id in selectedIds }.forEach { game ->
                if (!game.labels.contains(label)) {
                    gameRepository.updateGame(game.copy(labels = (game.labels + label).distinct()))
                }
            }
        }
    }

    fun removeLabelFromSelected(label: String) {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            gameRepository.getAllGames().first().filter { it.id in selectedIds }.forEach { game ->
                if (game.labels.contains(label)) {
                    gameRepository.updateGame(game.copy(labels = game.labels - label))
                }
            }
        }
    }

    fun addLabelToGame(gameId: Int, label: String) {
        if (label.isBlank()) return
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                if (!game.labels.contains(label)) {
                    gameRepository.updateGame(game.copy(labels = (game.labels + label).distinct()))
                }
            }
        }
    }

    fun removeLabelFromGame(gameId: Int, label: String) {
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                gameRepository.updateGame(game.copy(labels = game.labels - label))
            }
        }
    }

    fun addGenreToGame(gameId: Int, genre: String) {
        if (genre.isBlank()) return
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                if (!game.genres.contains(genre)) {
                    gameRepository.updateGame(game.copy(genres = (game.genres + genre).distinct()))
                }
            }
        }
    }

    fun removeGenreFromGame(gameId: Int, genre: String) {
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                gameRepository.updateGame(game.copy(genres = game.genres - genre))
            }
        }
    }

    fun deleteGame(game: Game) {
        viewModelScope.launch {
            gameRepository.deleteGame(game)
        }
    }

    fun updateGame(game: Game) {
        viewModelScope.launch {
            gameRepository.updateGame(game)
        }
    }

    fun updateReleaseDate(gameId: Int, newDate: String) {
        viewModelScope.launch {
            val games = gameRepository.getAllGames().first()
            games.find { it.id == gameId }?.let { game ->
                val normalized = gameRepository.normalizeDate(newDate) ?: newDate
                gameRepository.updateGame(game.copy(
                    releaseDate = normalized,
                    isReleaseDateManual = true
                ))
            }
        }
    }
}
