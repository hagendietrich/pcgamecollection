package com.example.digitalcollectionmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.example.digitalcollectionmanager.data.api.EaClient
import com.example.digitalcollectionmanager.data.api.EpicClient
import com.example.digitalcollectionmanager.data.api.GogClient
import com.example.digitalcollectionmanager.data.api.IgdbClient
import com.example.digitalcollectionmanager.data.api.SteamClient
import com.example.digitalcollectionmanager.data.repository.GameRepository
import com.example.digitalcollectionmanager.data.repository.SettingsRepository
import com.example.digitalcollectionmanager.ui.navigation.AppNavGraph
import com.example.digitalcollectionmanager.ui.navigation.Screen
import com.example.digitalcollectionmanager.ui.theme.DigitalCollectionManagerTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val settingsRepository = SettingsRepository(applicationContext)
        val igdbClient = IgdbClient()
        val steamClient = SteamClient()
        val gogClient = GogClient()
        val epicClient = EpicClient()
        
        val gameRepository = GameRepository(
            (application as App).database.gameDao(),
            (application as App).database.ignoredGameDao(),
            igdbClient,
            steamClient,
            gogClient,
            epicClient,
            settingsRepository
        )

        setContent {
            DigitalCollectionManagerTheme {
                val navController = rememberNavController()
                val isConfigured by settingsRepository.isConfigured.collectAsState(initial = null)

                if (isConfigured != null) {
                    AppNavGraph(
                        navController = navController,
                        settingsRepository = settingsRepository,
                        gameRepository = gameRepository,
                        igdbClient = igdbClient,
                        startDestination = if (isConfigured == true) Screen.Library.route else Screen.Setup.route
                    )
                }
            }
        }
    }
}
