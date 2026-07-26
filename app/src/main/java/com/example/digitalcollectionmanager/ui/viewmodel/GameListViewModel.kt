package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.GroupingType
import com.example.digitalcollectionmanager.data.model.SortOrder
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

    val isMultiSelectMode: StateFlow<Boolean> = _selectedGameIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val groupingType: StateFlow<GroupingType> = settingsRepository.groupingType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupingType.NONE)

    val groupedGames: StateFlow<Map<String, List<Game>>> = combine(
        gameRepository.getAllGames(),
        settingsRepository.sortOrder,
        settingsRepository.groupingType
    ) { games, sortOrder, groupingType ->
        val sorted = when (sortOrder) {
            SortOrder.TITLE_ASC -> games.sortedBy { it.title.lowercase() }
            SortOrder.RELEASE_DATE_ASC -> games.sortedBy { it.releaseDate }
            SortOrder.RELEASE_DATE_DESC -> games.sortedByDescending { it.releaseDate }
            SortOrder.PLAYTIME_ASC -> games.sortedBy { it.playtimeMinutes }
            SortOrder.PLAYTIME_DESC -> games.sortedByDescending { it.playtimeMinutes }
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
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val columnCount: StateFlow<Int> = settingsRepository.columnCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val sortOrder: StateFlow<SortOrder> = settingsRepository.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.TITLE_ASC)

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
                    gameRepository.updateGame(game.copy(labels = game.labels + label))
                }
            }
            clearSelection()
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
}
