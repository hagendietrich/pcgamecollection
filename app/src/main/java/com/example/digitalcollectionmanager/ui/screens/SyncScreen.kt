package com.example.digitalcollectionmanager.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.digitalcollectionmanager.R
import com.example.digitalcollectionmanager.ui.components.AppTopBar
import com.example.digitalcollectionmanager.ui.components.UnmatchedGameRow
import com.example.digitalcollectionmanager.ui.viewmodel.SyncUiState
import com.example.digitalcollectionmanager.ui.viewmodel.SyncViewModel

@Composable
fun SyncScreen(
    viewModel: SyncViewModel,
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
                title = stringResource(R.string.sync_title),
                onNavigate = onNavigate,
                showSearchToggle = false
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Display
            when (val state = uiState) {
                is SyncUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = { viewModel.cancelSync() }) {
                                Text("Cancel", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                is SyncUiState.Success -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                                TextButton(onClick = { viewModel.resetState() }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Dismiss")
                                }
                            }
                        }
                        
                        if (state.unmatchedGames.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Unmatched Games (${state.unmatchedGames.size})",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "We found these in your accounts but couldn't match them on IGDB. Tap to resolve.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.unmatchedGames) { unmatched ->
                                    UnmatchedGameRow(
                                        unmatched = unmatched,
                                        onResolve = { selection -> 
                                            viewModel.resolveUnmatchedGame(unmatched, selection) 
                                        },
                                        onSearchCustom = { query ->
                                            viewModel.searchCustomCandidates(unmatched, query)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                is SyncUiState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer)
                            Row(modifier = Modifier.align(Alignment.End)) {
                                TextButton(onClick = { viewModel.resetState() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }
                else -> {}
            }

            // Sync Section
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Button(
                        onClick = { viewModel.syncAllAccounts() },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState !is SyncUiState.Loading && (steamUrl.isNotBlank() || gogUsername.isNotBlank())
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_all))
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Steam Sync", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = steamUrl,
                                onValueChange = { steamUrl = it },
                                label = { Text("Steam Profile URL or ID") },
                                placeholder = { Text("e.g. tarrega8472") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { viewModel.syncSteam(steamUrl) },
                                modifier = Modifier.align(Alignment.End),
                                enabled = uiState !is SyncUiState.Loading
                            ) {
                                Text("Sync Steam")
                            }
                        }
                    }
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("GOG Sync", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = gogUsername,
                                onValueChange = { gogUsername = it },
                                label = { Text("GOG Username") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { viewModel.syncGog(gogUsername) },
                                modifier = Modifier.align(Alignment.End),
                                enabled = uiState !is SyncUiState.Loading
                            ) {
                                Text("Sync GOG")
                            }
                        }
                    }
                }
            }
        }
    }
}
