package com.example.digitalcollectionmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.ui.screens.AddGameScreen
import com.example.digitalcollectionmanager.ui.screens.GameListScreen
import com.example.digitalcollectionmanager.ui.screens.SetupScreen
import com.example.digitalcollectionmanager.ui.viewmodel.AddGameViewModel
import com.example.digitalcollectionmanager.ui.viewmodel.GameListViewModel
import com.example.digitalcollectionmanager.ui.viewmodel.SetupViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object AddGame : Screen("add_game")
    object Setup : Screen("setup")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
    gameRepository: GameRepository,
    igdbClient: IgdbClient,
    modifier: Modifier = Modifier,
    startDestination: String = Screen.Library.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.Library.route) {
            val viewModel: GameListViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GameListViewModel(gameRepository, settingsRepository) as T
                    }
                }
            )
            GameListScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onAddGame = { navController.navigate(Screen.AddGame.route) }
            )
        }
        composable(Screen.AddGame.route) {
            val viewModel: AddGameViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AddGameViewModel(gameRepository) as T
                    }
                }
            )
            AddGameScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Setup.route) {
            val viewModel: SetupViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SetupViewModel(settingsRepository, igdbClient) as T
                    }
                }
            )
            val uiState by viewModel.uiState.collectAsState()
            val clientId by settingsRepository.clientId.collectAsState(initial = "")
            val clientSecret by settingsRepository.clientSecret.collectAsState(initial = "")

            SetupScreen(
                uiState = uiState,
                initialClientId = clientId ?: "",
                initialClientSecret = clientSecret ?: "",
                isSettingsMode = true, // We only show it in the graph via menu now, or as start destination
                onSave = { id, secret -> viewModel.saveAndConnect(id, secret) },
                onSuccess = { 
                    navController.popBackStack()
                },
                onNavigate = { route -> 
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
