package com.example.digitalcollectionmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.ui.screens.SetupScreen
import com.example.digitalcollectionmanager.ui.theme.DigitalCollectionManagerTheme
import com.example.digitalcollectionmanager.ui.viewmodel.SetupViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(applicationContext)
        val igdbClient = IgdbClient()

        setContent {
            DigitalCollectionManagerTheme {
                val isConfigured by settingsRepository.isConfigured.collectAsState(initial = null)

                when (isConfigured) {
                    null -> { /* Loading state or Splash */ }
                    false -> {
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
                        val uiState by setupViewModel.uiState.collectAsState()
                        SetupScreen(
                            uiState = uiState,
                            onSave = { id, secret -> setupViewModel.saveAndConnect(id, secret) },
                            onSuccess = { /* The flow will automatically update via collectAsState */ }
                        )
                    }
                    true -> {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Greeting(
                                name = "Configured!",
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
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
