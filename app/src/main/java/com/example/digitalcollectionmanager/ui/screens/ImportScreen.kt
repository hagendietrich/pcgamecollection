package com.example.digitalcollectionmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.digitalcollectionmanager.ui.components.AppTopBar
import com.example.digitalcollectionmanager.ui.viewmodel.ImportUiState
import com.example.digitalcollectionmanager.ui.viewmodel.ImportViewModel

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val lastSteamId by viewModel.lastSteamId.collectAsState()
    val lastGogUsername by viewModel.lastGogUsername.collectAsState()

    var steamUrl by remember(lastSteamId) { mutableStateOf(lastSteamId) }
    var gogUsername by remember(lastGogUsername) { mutableStateOf(lastGogUsername) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Import Games",
                onNavigate = onNavigate
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Import from External Stores",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Make sure your profiles are set to 'Public' to allow the app to fetch your library.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Steam Import Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Steam Import", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = steamUrl,
                        onValueChange = { steamUrl = it },
                        label = { Text("Steam Profile URL or ID") },
                        placeholder = { Text("e.g. https://steamcommunity.com/id/name") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { viewModel.importSteam(steamUrl) },
                        modifier = Modifier.align(Alignment.End),
                        enabled = uiState !is ImportUiState.Loading
                    ) {
                        Text("Import Steam")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // GOG Import Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("GOG Import", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = gogUsername,
                        onValueChange = { gogUsername = it },
                        label = { Text("GOG Username") },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = { viewModel.importGog(gogUsername) },
                        modifier = Modifier.align(Alignment.End),
                        enabled = uiState !is ImportUiState.Loading
                    ) {
                        Text("Import GOG")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Status Display with Progress Bar
            when (val state = uiState) {
                is ImportUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
                is ImportUiState.Success -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            TextButton(onClick = { viewModel.resetState() }) {
                                Text("Dismiss")
                            }
                        }
                    }
                }
                is ImportUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            
                            Row {
                                if (state.message.contains("API Key")) {
                                    TextButton(onClick = { onNavigate(com.example.digitalcollectionmanager.ui.navigation.Screen.Setup.route) }) {
                                        Text("Go to Settings")
                                    }
                                }
                                TextButton(onClick = { viewModel.resetState() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}
