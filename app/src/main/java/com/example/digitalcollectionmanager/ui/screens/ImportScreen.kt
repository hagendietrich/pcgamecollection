package com.example.digitalcollectionmanager.ui.screens

import androidx.compose.foundation.BorderStroke
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
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.repository.UnmatchedGame
import com.example.digitalcollectionmanager.ui.components.AppTopBar
import com.example.digitalcollectionmanager.ui.components.UnmatchedGameRow
import com.example.digitalcollectionmanager.ui.viewmodel.ImportUiState
import com.example.digitalcollectionmanager.ui.viewmodel.ImportViewModel
import com.example.digitalcollectionmanager.ui.viewmodel.PlayniteImportState

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playniteImportState by viewModel.playniteImportState.collectAsState()
    val context = LocalContext.current

    var showWipeConfirm by remember { mutableStateOf(false) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToJson(it, context.contentResolver) }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.importFromJson(it, context.contentResolver) }
    }

    val playniteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.parsePlayniteJson(it, context.contentResolver) }
    }

    if (playniteImportState is PlayniteImportState.MappingRequired) {
        val mappingRequired = playniteImportState as PlayniteImportState.MappingRequired
        StatusMappingDialog(
            uniqueStatuses = mappingRequired.uniqueStatuses,
            onConfirm = { mapping: Map<String, CompletionStatus> -> viewModel.startPlayniteImport(mapping) },
            onDismiss = { viewModel.cancelPlayniteImport() }
        )
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
                                        onIgnore = { viewModel.ignoreUnmatchedGame(unmatched) },
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
                            Text("Playnite Import", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Import games from a Playnite JSON export file.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { playniteLauncher.launch("application/json") },
                                modifier = Modifier.align(Alignment.End),
                                enabled = uiState !is ImportUiState.Loading
                            ) {
                                Text("Select Playnite JSON")
                            }
                        }
                    }
                }


                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Data Portability", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Backup your entire library to a JSON file or restore from a previous backup.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { jsonImportLauncher.launch("application/json") },
                                    enabled = uiState !is ImportUiState.Loading,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Import (JSON)")
                                }
                                Button(
                                    onClick = { jsonExportLauncher.launch("digital_collection_backup.json") },
                                    enabled = uiState !is ImportUiState.Loading
                                ) {
                                    Text("Export (JSON)")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Danger Zone", 
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Permanently delete all games and data from your local library.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            Button(
                                onClick = { showWipeConfirm = true },
                                modifier = Modifier.align(Alignment.End),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                enabled = uiState !is ImportUiState.Loading
                            ) {
                                Text("Delete Library")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Delete Entire Library?") },
            text = { Text("This will permanently remove all games, playtimes, genres, and labels. This action cannot be undone. We recommend exporting a JSON backup first.") },
            confirmButton = {
                Button(
                    onClick = { 
                        viewModel.wipeLibrary()
                        showWipeConfirm = false 
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun StatusMappingDialog(
    uniqueStatuses: List<String>,
    onConfirm: (Map<String, CompletionStatus>) -> Unit,
    onDismiss: () -> Unit
) {
    val mapping = remember { mutableStateMapOf<String, CompletionStatus>() }
    
    // Default mapping
    LaunchedEffect(uniqueStatuses) {
        uniqueStatuses.forEach { status ->
            val mapped = when {
                status.contains("Complete", ignoreCase = true) -> CompletionStatus.COMPLETED
                status.contains("Playing", ignoreCase = true) -> CompletionStatus.PLAYING
                status.contains("Hold", ignoreCase = true) -> CompletionStatus.ON_HOLD
                status.contains("Abandoned", ignoreCase = true) -> CompletionStatus.ABANDONED
                status.contains("Interest", ignoreCase = true) -> CompletionStatus.ABANDONED
                else -> CompletionStatus.BACKLOG
            }
            mapping[status] = mapped
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Map Playnite Statuses") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    "We found these statuses in your Playnite file. Map them to your collection's categories:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uniqueStatuses) { status ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(status, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                TextButton(onClick = { expanded = true }) {
                                    Text(mapping[status]?.name ?: "Select...")
                                }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    CompletionStatus.entries.forEach { completionStatus ->
                                        DropdownMenuItem(
                                            text = { Text(completionStatus.name) },
                                            onClick = {
                                                mapping[status] = completionStatus
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(mapping.toMap()) }) {
                Text("Start Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
