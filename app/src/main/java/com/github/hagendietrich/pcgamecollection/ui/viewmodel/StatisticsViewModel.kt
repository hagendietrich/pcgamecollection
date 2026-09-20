package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatisticsUiState(
    val totalOwnedGames: Int = 0,
    val gamesByPlatform: Map<String, Int> = emptyMap(),
    val gamesByStatus: Map<CompletionStatus, Int> = emptyMap(),
    val totalPlaytimeMinutes: Int = 0,
    val topGamesByPlaytime: List<Game> = emptyList(),
    val genreDistribution: Map<String, Int> = emptyMap()
)

class StatisticsViewModel(
    private val gameRepository: GameRepository
) : ViewModel() {

    val uiState: StateFlow<StatisticsUiState> = gameRepository.getAllGames()
        .map { games ->
            val ownedGames = games.filter { it.isOwned }
            
            val platformMap = mutableMapOf<String, Int>()
            ownedGames.forEach { game ->
                game.platforms.forEach { platform ->
                    platformMap[platform] = (platformMap[platform] ?: 0) + 1
                }
            }

            val statusCounts = ownedGames.groupBy { it.completionStatus }
                .mapValues { it.value.size }
            
            val statusMap = CompletionStatus.entries.associateWith { status ->
                statusCounts[status] ?: 0
            }.toSortedMap()

            val totalPlaytime = ownedGames.sumOf { it.playtimeMinutes }
            
            val topGames = ownedGames.sortedByDescending { it.playtimeMinutes }.take(5)
            
            val genresMap = mutableMapOf<String, Int>()
            ownedGames.forEach { game ->
                game.genres.forEach { genre ->
                    genresMap[genre] = (genresMap[genre] ?: 0) + 1
                }
            }
            val topGenres = genresMap.toList()
                .sortedByDescending { it.second }
                .take(10)
                .toMap()

            StatisticsUiState(
                totalOwnedGames = ownedGames.size,
                gamesByPlatform = platformMap.toList().sortedByDescending { it.second }.toMap(),
                gamesByStatus = statusMap,
                totalPlaytimeMinutes = totalPlaytime,
                topGamesByPlaytime = topGames,
                genreDistribution = topGenres
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StatisticsUiState()
        )
}
