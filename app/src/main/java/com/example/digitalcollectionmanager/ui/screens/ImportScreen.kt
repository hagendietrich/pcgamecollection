package com.example.digitalcollectionmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.digitalcollectionmanager.R
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.repository.UnmatchedGame
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
    val context = LocalContext.current

    var steamUrl by remember(lastSteamId) { mutableStateOf(lastSteamId) }
    var gogUsername by remember(lastGogUsername) { mutableStateOf(lastGogUsername) }

    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportToCsv(it, context.contentResolver) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.import_export_title),
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
            Text(
                text = stringResource(R.string.menu_import),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Status Display
            when (val state = uiState) {
                is ImportUiState.Loading -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp)
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                is ImportUiState.Success -> {
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
                                "We found these in your library but couldn't match them on IGDB. Tap a game to find the correct match.",
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
                is ImportUiState.Error -> {
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

            // Forms Section
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Steam Import", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = steamUrl,
                                onValueChange = { steamUrl = it },
                                label = { Text("Steam Profile URL or ID") },
                                placeholder = { Text("e.g. tarrega8472") },
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
                }

                item {
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
                }

                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Export Collection", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Backup your entire library to a CSV file.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { csvExportLauncher.launch("digital_collection_export.csv") },
                                modifier = Modifier.align(Alignment.End),
                                enabled = uiState !is ImportUiState.Loading
                            ) {
                                Text("Export to CSV")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UnmatchedGameRow(
    unmatched: UnmatchedGame,
    onResolve: (IgdbGame) -> Unit,
    onSearchCustom: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember(unmatched.storeTitle) { mutableStateOf(unmatched.storeTitle) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(unmatched.storeTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("${unmatched.platform} ID: ${unmatched.storeId}", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (expanded) "Close" else "Resolve", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search on IGDB") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        singleLine = true
                    )
                    Button(onClick = { onSearchCustom(searchText) }) {
                        Text("Search")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select the correct match:", style = MaterialTheme.typography.labelMedium)
                
                if (unmatched.candidates.isEmpty()) {
                    Text("No candidates found. Try refining the search above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    unmatched.candidates.forEach { candidate ->
                        val year = candidate.firstReleaseDate?.let { 
                            java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).year 
                        }
                        
                        ListItem(
                            headlineContent = { Text(candidate.name) },
                            supportingContent = { Text(year?.toString() ?: "Unknown Year") },
                            modifier = Modifier.clickable { 
                                onResolve(candidate) 
                                expanded = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}
