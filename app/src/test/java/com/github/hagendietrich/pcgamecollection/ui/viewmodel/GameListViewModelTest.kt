package com.github.hagendietrich.pcgamecollection.ui.viewmodel

import app.cash.turbine.test
import com.github.hagendietrich.pcgamecollection.data.model.*
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import com.github.hagendietrich.pcgamecollection.data.repository.SettingsRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameListViewModelTest {

    private val gameRepository = mockk<GameRepository>()
    private val settingsRepository = mockk<SettingsRepository>()
    private lateinit var viewModel: GameListViewModel
    
    // Using UnconfinedTestDispatcher for immediate execution in tests
    private val testDispatcher = UnconfinedTestDispatcher()

    private val sampleGames = listOf(
        Game(id = 1, title = "Witcher 3", coverImageUrl = null, genres = listOf("RPG"), platforms = listOf("GOG"), completionStatus = CompletionStatus.COMPLETED),
        Game(id = 2, title = "Cyberpunk 2077", coverImageUrl = null, genres = listOf("RPG", "FPS"), platforms = listOf("Steam"), completionStatus = CompletionStatus.PLAYING),
        Game(id = 3, title = "Doom", coverImageUrl = null, genres = listOf("FPS"), platforms = listOf("Steam"), completionStatus = CompletionStatus.BACKLOG)
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { gameRepository.getAllGames() } returns flowOf(sampleGames)
        every { settingsRepository.sortOrder } returns flowOf(SortOrder.TITLE_ASC)
        every { settingsRepository.groupingType } returns flowOf(GroupingType.NONE)
        every { settingsRepository.libraryFilters } returns flowOf(LibraryFilters())
        every { settingsRepository.columnCount } returns flowOf(3)
        
        viewModel = GameListViewModel(gameRepository, settingsRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `allGames state flow emits repository games`() = runTest {
        viewModel.allGames.test {
            // StateFlow emits initial value, but with UnconfinedTestDispatcher it might skip straight to data
            // Let's use skipItems if needed, but Unconfined often emits immediately.
            val item = awaitItem()
            if (item.isEmpty()) {
                assertEquals(sampleGames, awaitItem())
            } else {
                assertEquals(sampleGames, item)
            }
        }
    }

    @Test
    fun `groupedGames applies search query correctly`() = runTest {
        viewModel.updateSearchQuery("Witcher")
        
        viewModel.groupedGames.test {
            // Skip initial emission or wait for search result
            var item = awaitItem()
            while (item.isEmpty() || (item[""]?.size ?: 0) != 1) {
                item = awaitItem()
            }
            assertEquals("Witcher 3", item[""]?.first()?.title)
        }
    }

    @Test
    fun `toggleSelection updates selectedGameIds and isMultiSelectMode`() = runTest {
        viewModel.selectedGameIds.test {
            assertEquals(emptySet<Int>(), awaitItem()) // Initial
            viewModel.toggleSelection(1)
            assertEquals(setOf(1), awaitItem())
            
            viewModel.isMultiSelectMode.test {
                assertTrue(awaitItem())
            }
            
            viewModel.toggleSelection(1)
            assertEquals(emptySet<Int>(), awaitItem())
        }
    }

    @Test
    fun `clearSelection resets selected IDs`() = runTest {
        viewModel.toggleSelection(1)
        viewModel.toggleSelection(2)
        
        viewModel.selectedGameIds.test {
            // May need to skip previous emissions depending on when we start testing
            val current = awaitItem() 
            // If it's not empty, we are at the state after toggles
            // If it IS empty, we wait.
            viewModel.clearSelection()
            val result = if (current.isNotEmpty()) awaitItem() else { awaitItem(); awaitItem() }
            assertTrue(result.isEmpty())
        }
    }

    @Test
    fun `groupedGames respects sorting order`() = runTest {
        val sortOrderFlow = MutableStateFlow(SortOrder.TITLE_ASC)
        every { settingsRepository.sortOrder } returns sortOrderFlow
        
        viewModel = GameListViewModel(gameRepository, settingsRepository)

        viewModel.groupedGames.test {
            // TITLE_ASC: Cyberpunk, Doom, Witcher
            var item = awaitItem()
            while (item.isEmpty()) item = awaitItem()
            
            assertEquals("Cyberpunk 2077", item[""]?.first()?.title)
            
            // Note: In a real scenario, changing sortOrderFlow would trigger a new emission
        }
    }
}
