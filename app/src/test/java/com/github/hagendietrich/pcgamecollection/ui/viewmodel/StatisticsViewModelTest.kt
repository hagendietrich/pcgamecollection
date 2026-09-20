package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import app.cash.turbine.test
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StatisticsViewModelTest {

    private val gameRepository = mockk<GameRepository>()
    private lateinit var viewModel: StatisticsViewModel
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleGames = listOf(
        Game(id = 1, title = "Game A", coverImageUrl = null, isOwned = true, platforms = listOf("Steam"), completionStatus = CompletionStatus.COMPLETED, playtimeMinutes = 60, genres = listOf("RPG")),
        Game(id = 2, title = "Game B", coverImageUrl = null, isOwned = true, platforms = listOf("Steam", "GOG"), completionStatus = CompletionStatus.PLAYING, playtimeMinutes = 120, genres = listOf("RPG", "Action")),
        Game(id = 3, title = "Game C", coverImageUrl = null, isOwned = false, platforms = listOf("Epic"), completionStatus = CompletionStatus.BACKLOG, playtimeMinutes = 0, genres = listOf("Strategy"))
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { gameRepository.getAllGames() } returns flowOf(sampleGames)
        viewModel = StatisticsViewModel(gameRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uiState aggregates owned games correctly`() = runTest {
        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(2, state.totalOwnedGames)
            assertEquals(180, state.totalPlaytimeMinutes)
            
            // Platform counts: Steam(2), GOG(1)
            assertEquals(2, state.gamesByPlatform["Steam"])
            assertEquals(1, state.gamesByPlatform["GOG"])
            
            // Status counts: COMPLETED(1), PLAYING(1), others(0)
            assertEquals(1, state.gamesByStatus[CompletionStatus.COMPLETED])
            assertEquals(1, state.gamesByStatus[CompletionStatus.PLAYING])
            assertEquals(0, state.gamesByStatus[CompletionStatus.ON_HOLD])
            assertEquals(0, state.gamesByStatus[CompletionStatus.BACKLOG])
            assertEquals(0, state.gamesByStatus[CompletionStatus.ABANDONED])
            
            // Top genres: RPG(2), Action(1)
            assertEquals(2, state.genreDistribution["RPG"])
            assertEquals(1, state.genreDistribution["Action"])
        }
    }
}
