package com.example.digitalcollectionmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.ui.screens.AddGameScreen
import com.example.digitalcollectionmanager.ui.screens.GameListScreen
import com.example.digitalcollectionmanager.ui.screens.SetupScreen
import com.example.digitalcollectionmanager.ui.theme.DigitalCollectionManagerTheme
import com.example.digitalcollectionmanager.ui.viewmodel.AddGameViewModel
import com.example.digitalcollectionmanager.ui.viewmodel.GameListViewModel
import com.example.digitalcollectionmanager.ui.viewmodel.SetupViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

enum class Screen {
    Setup, Library, AddGame
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(applicationContext)
        val igdbClient = IgdbClient()
        val gameRepository = GameRepository(
            (application as App).database.gameDao(),
            igdbClient,
            settingsRepository
        )

        setContent {
            DigitalCollectionManagerTheme {
                val isConfigured by settingsRepository.isConfigured.collectAsState(initial = null)
                var currentScreen by remember { mutableStateOf(Screen.Library) }

                LaunchedEffect(isConfigured) {
                    if (isConfigured == false) {
                        currentScreen = Screen.Setup
                    } else if (isConfigured == true && currentScreen == Screen.Setup) {
                        currentScreen = Screen.Library
                    }
                }

                when (currentScreen) {
                    Screen.Setup -> {
                        val setupViewModel: SetupViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(SetupViewModel::class.java)) {
                                        @Suppress("UNCHECKED_CAST")
                                        return SetupViewModel(settingsRepository, igdbClient) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
                                }
                            }
                        )
                        SetupScreen(
                            uiState = setupViewModel.uiState.collectAsState().value,
                            onSave = { id, secret -> setupViewModel.saveAndConnect(id, secret) },
                            onSuccess = { currentScreen = Screen.Library }
                        )
                    }
                    Screen.Library -> {
                        val gameListViewModel: GameListViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(GameListViewModel::class.java)) {
                                        @Suppress("UNCHECKED_CAST")
                                        return GameListViewModel(gameRepository, settingsRepository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
                                }
                            }
                        )
                        GameListScreen(
                            viewModel = gameListViewModel,
                            onAddGame = { currentScreen = Screen.AddGame }
                        )
                    }
                    Screen.AddGame -> {
                        val addGameViewModel: AddGameViewModel = viewModel(
                            factory = object : ViewModelProvider.Factory {
                                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                    if (modelClass.isAssignableFrom(AddGameViewModel::class.java)) {
                                        @Suppress("UNCHECKED_CAST")
                                        return AddGameViewModel(gameRepository) as T
                                    }
                                    throw IllegalArgumentException("Unknown ViewModel class")
                                }
                            }
                        )
                        AddGameScreen(
                            viewModel = addGameViewModel,
                            onBack = { currentScreen = Screen.Library }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DigitalCollectionManagerTheme {
        Greeting("Android")
    }
}
