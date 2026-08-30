package com.github.hagendietrich.pcgamecollection

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.github.hagendietrich.pcgamecollection.data.api.BattleNetClient
import com.github.hagendietrich.pcgamecollection.data.api.EaClient
import com.github.hagendietrich.pcgamecollection.data.api.EpicClient
import com.github.hagendietrich.pcgamecollection.data.api.GogClient
import com.github.hagendietrich.pcgamecollection.data.api.HltbClient
import com.github.hagendietrich.pcgamecollection.data.api.IgdbClient
import com.github.hagendietrich.pcgamecollection.data.api.SteamClient
import com.github.hagendietrich.pcgamecollection.data.api.UbisoftClient
import com.github.hagendietrich.pcgamecollection.data.repository.GameRepository
import com.github.hagendietrich.pcgamecollection.data.repository.SettingsRepository
import com.github.hagendietrich.pcgamecollection.ui.navigation.AppNavGraph
import com.github.hagendietrich.pcgamecollection.ui.navigation.Screen
import com.github.hagendietrich.pcgamecollection.ui.theme.PcGameCollectionTheme

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
        val ubisoftClient = UbisoftClient()
        val battleNetClient = BattleNetClient()
        val hltbClient = HltbClient()
        
        val gameRepository = GameRepository(
            (application as App).database.gameDao(),
            (application as App).database.ignoredGameDao(),
            (application as App).database.wishlistGameDao(),
            igdbClient,
            steamClient,
            gogClient,
            epicClient,
            ubisoftClient,
            battleNetClient,
            hltbClient,
            settingsRepository
        )

        setContent {
            PcGameCollectionTheme {
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
