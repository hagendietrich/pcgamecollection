package com.github.hagendietrich.pcgamecollection.ui.navigation

import androidx.compose.animation.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.hagendietrich.pcgamecollection.data.api.IgdbClient
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import com.github.hagendietrich.pcgamecollection.data.repository.SettingsRepository
import com.github.hagendietrich.pcgamecollection.ui.screens.*
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(val route: String) {
    object Library : Screen("library")
    object Wishlist : Screen("wishlist")
    object AddGame : Screen("add_game")
    object Setup : Screen("setup")
    object Sync : Screen("sync")
    object ImportExport : Screen("import_export")
    object About : Screen("about")
    object GameDetails : Screen("game_details/{gameId}") {
        fun createRoute(gameId: Int) = "game_details/$gameId"
    }
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
        composable(
            route = Screen.Library.route,
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it / 3 }) + fadeOut()
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it / 3 }) + fadeIn()
            }
        ) {
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
                onAddGame = { navController.navigate(Screen.AddGame.route) },
                onShowFullDetails = { gameId ->
                    navController.navigate(Screen.GameDetails.createRoute(gameId))
                }
            )
        }
        composable(Screen.Wishlist.route) {
            val viewModel: WishlistViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return WishlistViewModel(gameRepository) as T
                    }
                }
            )
            WishlistScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) }
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
        composable(Screen.ImportExport.route) {
            val viewModel: ImportViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ImportViewModel(gameRepository, settingsRepository) as T
                    }
                }
            )
            ImportScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Sync.route) {
            val viewModel: SyncViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SyncViewModel(gameRepository, settingsRepository) as T
                    }
                }
            )
            SyncScreen(
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.GameDetails.route,
            arguments = listOf(navArgument("gameId") { type = NavType.IntType }),
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it })
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it })
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it })
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it })
            }
        ) { backStackEntry ->
            val gameId = backStackEntry.arguments?.getInt("gameId") ?: 0
            val viewModel: GameDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return GameDetailViewModel(gameId, gameRepository) as T
                    }
                }
            )
            GameDetailScreen(
                viewModel = viewModel,
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
            val steamApiKey by settingsRepository.steamApiKey.collectAsState(initial = "")

            SetupScreen(
                uiState = uiState,
                initialClientId = clientId ?: "",
                initialClientSecret = clientSecret ?: "",
                initialSteamApiKey = steamApiKey ?: "",
                isSettingsMode = true,
                onSave = { id, secret, steamKey -> viewModel.saveAndConnect(id, secret, steamKey) },
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
        composable(Screen.About.route) {
            AboutScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
