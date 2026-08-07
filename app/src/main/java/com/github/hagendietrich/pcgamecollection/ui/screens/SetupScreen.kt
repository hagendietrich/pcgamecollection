package com.github.hagendietrich.pcgamecollection.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.ui.components.AppTopBar
import com.github.hagendietrich.pcgamecollection.ui.theme.PcGameCollectionTheme
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.SetupUiState
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.SetupViewModel

@Composable
fun SetupScreen(
    uiState: SetupUiState,
    initialClientId: String = "",
    initialClientSecret: String = "",
    initialSteamApiKey: String = "",
    isSettingsMode: Boolean = false,
    onSave: (String, String, String) -> Unit,
    onSuccess: () -> Unit,
    onNavigate: (String) -> Unit = {}
) {
    var clientId by remember { mutableStateOf(initialClientId) }
    var clientSecret by remember { mutableStateOf(initialClientSecret) }
    var steamApiKey by remember { mutableStateOf(initialSteamApiKey) }
    var showIgdbHelp by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is SetupUiState.Success) {
            onSuccess()
        }
    }

    Scaffold(
        topBar = {
            if (isSettingsMode) {
                AppTopBar(
                    title = stringResource(R.string.setup_update),
                    onNavigate = onNavigate,
                    showSearchToggle = false
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isSettingsMode) stringResource(R.string.setup_update) else stringResource(R.string.setup_welcome),
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = { showIgdbHelp = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Help,
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.setup_instruction),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = clientId,
                onValueChange = { clientId = it },
                label = { Text(stringResource(R.string.setup_client_id)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = clientSecret,
                onValueChange = { clientSecret = it },
                label = { Text(stringResource(R.string.setup_client_secret)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "External Store APIs",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = steamApiKey,
                onValueChange = { steamApiKey = it },
                label = { Text(stringResource(R.string.setup_steam_api_key)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Text(
                text = "Get your key at: https://steamcommunity.com/dev/apikey (Use 'localhost' as domain)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp).align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(24.dp))

            if (uiState is SetupUiState.Error) {
                Text(
                    text = when (uiState.message) {
                        "Please enter both Client ID and Client Secret" -> stringResource(R.string.setup_error_empty)
                        "Invalid credentials. Please check your Client ID and Secret." -> stringResource(R.string.setup_error_invalid)
                        "Please enter IGDB credentials or a Steam API Key." -> "Please enter IGDB credentials or a Steam API Key."
                        "Invalid IGDB credentials. Please check your Client ID and Secret." -> stringResource(R.string.setup_error_invalid)
                        else -> uiState.message
                    },
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { onSave(clientId, clientSecret, steamApiKey) },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is SetupUiState.Loading
            ) {
                if (uiState is SetupUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (isSettingsMode) stringResource(R.string.setup_save) else stringResource(R.string.setup_connect))
                }
            }

            if (showIgdbHelp) {
                AlertDialog(
                    onDismissRequest = { showIgdbHelp = false },
                    title = { Text(stringResource(R.string.setup_help_igdb_title)) },
                    text = { Text(stringResource(R.string.setup_help_igdb_content)) },
                    confirmButton = {
                        TextButton(onClick = { showIgdbHelp = false }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SetupScreenPreview() {
    PcGameCollectionTheme {
        SetupScreen(
            uiState = SetupUiState.Idle,
            isSettingsMode = true,
            onSave = { _, _, _ -> },
            onSuccess = {},
            onNavigate = {}
        )
    }
}
