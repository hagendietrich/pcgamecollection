package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.model.*
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

    val libraryFilters: StateFlow<LibraryFilters> = settingsRepository.libraryFilters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryFilters())

    val groupedGames: StateFlow<Map<String, List<Game>>> = combine(
        gameRepository.getAllGames(),
        settingsRepository.sortOrder,
        settingsRepository.groupingType,
        settingsRepository.libraryFilters,
        _searchQuery
    ) { games, sortOrder, groupingType, filters, query ->
        val searched = if (query.isBlank()) {
            games
        } else {
            games.filter { it.title.contains(query, ignoreCase = true) }
        }

        val filtered = searched.filter { game ->
            // Filter by Mode
            if (!passesFilter(game.gameModes, filters.modes)) return@filter false
            // Filter by Platform
            if (!passesFilter(game.platforms, filters.platforms)) return@filter false
            // Filter by Status (Single value)
            if (!passesFilter(listOf(game.completionStatus.name), filters.statuses)) return@filter false
            // Filter by Labels
            if (!passesFilter(game.labels, filters.labels)) return@filter false
            // Filter by Genre
            if (!passesFilter(game.genres, filters.genres)) return@filter false
            
            true
        }

        val sorted = when (sortOrder) {
            SortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            SortOrder.RELEASE_DATE_ASC -> filtered.sortedBy { gameRepository.normalizeDate(it.releaseDate) ?: "" }
            SortOrder.RELEASE_DATE_DESC -> filtered.sortedByDescending { gameRepository.normalizeDate(it.releaseDate) ?: "" }
            SortOrder.PLAYTIME_ASC -> filtered.sortedBy { it.playtimeMinutes }
            SortOrder.PLAYTIME_DESC -> filtered.sortedByDescending { it.playtimeMinutes }
            SortOrder.DATE_ADDED_ASC -> filtered.sortedBy { it.dateAdded }
            SortOrder.DATE_ADDED_DESC -> filtered.sortedByDescending { it.dateAdded }
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

    val allPlatforms: StateFlow<List<String>> = gameRepository.getAllGames()
        .map { games -> games.flatMap { it.platforms }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _alternativeCovers = MutableStateFlow<List<String>>(emptyList())
    val alternativeCovers: StateFlow<List<String>> = _alternativeCovers.asStateFlow()

    private val _isFetchingCovers = MutableStateFlow(false)
    val isFetchingCovers: StateFlow<Boolean> = _isFetchingCovers.asStateFlow()

    private val _isSearchingReMatch = MutableStateFlow(false)
    val isSearchingReMatch: StateFlow<Boolean> = _isSearchingReMatch.asStateFlow()

    private val _reMatchResults = MutableStateFlow<List<IgdbGame>>(emptyList())
    val reMatchResults: StateFlow<List<IgdbGame>> = _reMatchResults.asStateFlow()

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

    fun applyReMatch(gameId: Int, selection: IgdbGame) {
        viewModelScope.launch {
            gameRepository.reMatchGame(gameId, selection)
            _reMatchResults.value = emptyList()
        }
    }

    fun clearReMatchResults() {
        _reMatchResults.value = emptyList()
    }

    fun fetchAlternativeCovers(gameId: Int) {
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

    fun updateGameCover(gameId: Int, imageUrl: String) {
        viewModelScope.launch {
            val games = gameRepository.getAllGames().first()
            games.find { it.id == gameId }?.let { game ->
                gameRepository.updateGame(game.copy(
                    coverImageUrl = imageUrl,
                    isCoverManual = true
                ))
            }
        }
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

    fun deleteSelectedGames() {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { gameRepository.deleteGame(it) }
            clearSelection()
        }
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
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                if (!game.labels.contains(label)) {
                    gameRepository.updateGame(game.copy(labels = (game.labels + label).distinct()))
                }
            }
        }
    }

    fun removeLabelFromSelected(label: String) {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                if (game.labels.contains(label)) {
                    gameRepository.updateGame(game.copy(labels = game.labels - label))
                }
            }
        }
    }

    fun addGenreToSelected(genre: String) {
        if (genre.isBlank()) return
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                if (!game.genres.contains(genre)) {
                    gameRepository.updateGame(game.copy(
                        genres = (game.genres + genre).distinct(),
                        isGenreManual = true
                    ))
                }
            }
        }
    }

    fun removeGenreFromSelected(genre: String) {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                if (game.genres.contains(genre)) {
                    gameRepository.updateGame(game.copy(
                        genres = game.genres - genre,
                        isGenreManual = true
                    ))
                }
            }
        }
    }

    fun addPlatformToSelected(platform: String) {
        if (platform.isBlank()) return
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                if (!game.platforms.contains(platform)) {
                    gameRepository.updateGame(game.copy(
                        platforms = (game.platforms + platform).distinct()
                    ))
                }
            }
        }
    }

    fun removePlatformFromSelected(platform: String) {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                if (game.platforms.contains(platform)) {
                    gameRepository.updateGame(game.copy(
                        platforms = game.platforms - platform
                    ))
                }
            }
        }
    }

    fun updateSelectedGameModes(modes: List<String>) {
        viewModelScope.launch {
            val selectedIds = _selectedGameIds.value
            val sortedModes = gameRepository.sortGameModes(modes)
            val games = gameRepository.getAllGames().first().filter { it.id in selectedIds }
            games.forEach { game ->
                gameRepository.updateGame(game.copy(
                    gameModes = sortedModes,
                    isGameModeManual = true
                ))
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
                    gameRepository.updateGame(game.copy(
                        genres = (game.genres + genre).distinct(),
                        isGenreManual = true
                    ))
                }
            }
        }
    }

    fun removeGenreFromGame(gameId: Int, genre: String) {
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                if (game.genres.contains(genre)) {
                    gameRepository.updateGame(game.copy(
                        genres = game.genres - genre,
                        isGenreManual = true
                    ))
                }
            }
        }
    }

    fun addPlatformToGame(gameId: Int, platform: String) {
        if (platform.isBlank()) return
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                if (!game.platforms.contains(platform)) {
                    gameRepository.updateGame(game.copy(
                        platforms = (game.platforms + platform).distinct()
                    ))
                }
            }
        }
    }

    fun removePlatformFromGame(gameId: Int, platform: String) {
        viewModelScope.launch {
            gameRepository.getAllGames().first().find { it.id == gameId }?.let { game ->
                if (game.platforms.contains(platform)) {
                    gameRepository.updateGame(game.copy(
                        platforms = game.platforms - platform
                    ))
                }
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

    fun updateGameModes(gameId: Int, modes: List<String>) {
        viewModelScope.launch {
            val games = gameRepository.getAllGames().first()
            games.find { it.id == gameId }?.let { game ->
                gameRepository.updateGame(game.copy(
                    gameModes = gameRepository.sortGameModes(modes),
                    isGameModeManual = true
                ))
            }
        }
    }

    private fun passesFilter(gameItems: List<String>, filterMap: Map<String, FilterType>): Boolean {
        if (filterMap.isEmpty()) return true
        
        val activeFilters = filterMap.filter { it.value != FilterType.NONE }
        if (activeFilters.isEmpty()) return true

        val includeSet = activeFilters.filter { it.value == FilterType.INCLUDE }.keys
        val excludeSet = activeFilters.filter { it.value == FilterType.EXCLUDE }.keys

        // Rule: ALWAYS exclude if ANY game item is in the exclude set
        if (gameItems.any { it in excludeSet }) return false

        // Rule: If include set is not empty, game items must match AT LEAST ONE in the include set
        if (includeSet.isNotEmpty()) {
            if (!gameItems.any { it in includeSet }) return false
        }

        return true
    }

    fun toggleFilter(category: String, item: String) {
        viewModelScope.launch {
            val current = libraryFilters.value
            val newFilters = when (category) {
                "Mode" -> current.copy(modes = cycleFilter(current.modes, item))
                "Platform" -> current.copy(platforms = cycleFilter(current.platforms, item))
                "Status" -> current.copy(statuses = cycleFilter(current.statuses, item))
                "Labels" -> current.copy(labels = cycleFilter(current.labels, item))
                "Genre" -> current.copy(genres = cycleFilter(current.genres, item))
                else -> current
            }
            settingsRepository.updateFilters(newFilters)
        }
    }

    private fun cycleFilter(map: Map<String, FilterType>, item: String): Map<String, FilterType> {
        val next = when (map[item] ?: FilterType.NONE) {
            FilterType.NONE -> FilterType.INCLUDE
            FilterType.INCLUDE -> FilterType.EXCLUDE
            FilterType.EXCLUDE -> FilterType.NONE
        }
        val mutable = map.toMutableMap()
        if (next == FilterType.NONE) mutable.remove(item) else mutable[item] = next
        return mutable
    }

    fun clearAllFilters() {
        viewModelScope.launch {
            settingsRepository.updateFilters(LibraryFilters())
        }
    }

    fun clearCategoryFilter(category: String) {
        viewModelScope.launch {
            val current = libraryFilters.value
            val newFilters = when (category) {
                "Mode" -> current.copy(modes = emptyMap())
                "Platform" -> current.copy(platforms = emptyMap())
                "Status" -> current.copy(statuses = emptyMap())
                "Labels" -> current.copy(labels = emptyMap())
                "Genre" -> current.copy(genres = emptyMap())
                else -> current
            }
            settingsRepository.updateFilters(newFilters)
        }
    }

    fun enrichGame(gameId: Int) {
        viewModelScope.launch {
            try {
                gameRepository.refreshGameMetadata(gameId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
