package com.example.digitalcollectionmanager.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.SortOrder
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameListViewModel(
    private val gameRepository: GameRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val games: StateFlow<List<Game>> = combine(
        gameRepository.getAllGames(),
        settingsRepository.sortOrder
    ) { games, sortOrder ->
        when (sortOrder) {
            SortOrder.TITLE_ASC -> games.sortedBy { it.title.lowercase() }
            SortOrder.RELEASE_DATE_ASC -> games.sortedBy { it.releaseDate }
            SortOrder.RELEASE_DATE_DESC -> games.sortedByDescending { it.releaseDate }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun deleteGame(game: Game) {
        viewModelScope.launch {
            gameRepository.deleteGame(game)
        }
    }
}
