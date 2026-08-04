package com.example.digitalcollectionmanager.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import com.example.digitalcollectionmanager.R
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.ui.components.AppTopBar
import com.example.digitalcollectionmanager.ui.components.UnmatchedGameRow
import com.example.digitalcollectionmanager.ui.viewmodel.ImportUiState
import com.example.digitalcollectionmanager.ui.viewmodel.ImportViewModel
import com.example.digitalcollectionmanager.ui.viewmodel.PlayniteImportState
import kotlinx.coroutines.launch

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val playniteImportState by viewModel.playniteImportState.collectAsState()
    val hasAllFilesAccess by viewModel.hasAllFilesAccess.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var showWipeConfirm by remember { mutableStateOf(false) }

    // Refresh permission status when returning to the app
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissionStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Tier 0 Bypass: Requesting standard storage permission can help Waydroid correctly resolve the app's package name
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("ImportScreen", "Storage permission granted: $isGranted")
    }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportToJson(it, context.contentResolver) }
    }

    val jsonImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importFromJson(it, context.contentResolver) }
    }

    val playniteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.parsePlayniteJson(it, context.contentResolver) }
    }

    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ -> }

    fun shareBackup() {
        coroutineScope.launch {
            try {
                val json = viewModel.getBackupJson()
                val tempFile = java.io.File(context.cacheDir, "dcm_backup_share.json")
                tempFile.writeText(json)
                
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    tempFile
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                shareLauncher.launch(Intent.createChooser(intent, "Share Backup JSON"))
            } catch (e: Exception) {
                Log.e("ImportScreen", "Share Error", e)
            }
        }
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
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                // Add "Grant All Files Access" button if on Android 11+ and it likely failed due to permissions
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
                                    (viewModel.isWaydroid || state.message.contains("All file access", ignoreCase = true) || state.message.contains("NPE", ignoreCase = true))) {
                                    TextButton(onClick = { 
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        intent.data = Uri.parse("package:" + context.packageName)
                                        context.startActivity(intent)
                                    }) {
                                        Text("Grant All Files Access")
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

            // Forms Section
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Waydroid Compatibility Card
                if (viewModel.isWaydroid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (hasAllFilesAccess) 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f) 
                                else 
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f)
                            ),
                            border = BorderStroke(1.dp, if (hasAllFilesAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Waydroid Compatibility", 
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (hasAllFilesAccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    "If standard import fails with a system crash, enable 'All Files Access' to use the Direct Bypass.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (hasAllFilesAccess) Icons.Default.CheckCircle else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (hasAllFilesAccess) Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (hasAllFilesAccess) " Direct Bypass Enabled" else " Direct Bypass Blocked",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (hasAllFilesAccess) Color(0xFF4CAF50) else Color(0xFFFF9800)
                                        )
                                    }
                                    
                                    Button(
                                        onClick = { 
                                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                            intent.data = Uri.parse("package:" + context.packageName)
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasAllFilesAccess) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                                        )
                                    ) {
                                        Text(if (hasAllFilesAccess) "Modify Permission" else "Grant Permission")
                                    }
                                }
                            }
                        }
                    }
                }

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
                                onClick = { 
                                    // Requesting permission as a bypass for Waydroid's broken package identity logic
                                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                    }
                                    playniteLauncher.launch(arrayOf("*/*")) 
                                },
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
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = { 
                                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                        jsonImportLauncher.launch(arrayOf("*/*")) 
                                    },
                                    enabled = uiState !is ImportUiState.Loading,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Import (JSON)")
                                }
                                Button(
                                    onClick = { jsonExportLauncher.launch("digital_collection_backup.json") },
                                    enabled = uiState !is ImportUiState.Loading,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Export (JSON)")
                                }
                                IconButton(
                                    onClick = { shareBackup() },
                                    enabled = uiState !is ImportUiState.Loading
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
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
