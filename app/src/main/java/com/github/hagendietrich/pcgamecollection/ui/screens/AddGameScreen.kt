package com.github.hagendietrich.pcgamecollection.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.res.stringResource
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.ui.components.AppTopBar
import com.github.hagendietrich.pcgamecollection.ui.theme.PcGameCollectionTheme
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.AddGameUiState
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.AddGameViewModel

@Composable
fun AddGameScreen(
    viewModel: AddGameViewModel,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val addedGameIds by viewModel.addedGameIds.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { gameName ->
            snackbarHostState.showSnackbar(context.getString(R.string.add_game_success, gameName))
            viewModel.clearSnackbar()
        }
    }

    AddGameContent(
        uiState = uiState,
        addedGameIds = addedGameIds,
        snackbarHostState = snackbarHostState,
        onSearch = { viewModel.searchGames(it) },
        onAdd = { viewModel.addGame(it) },
        onBack = onBack,
        onNavigate = onNavigate
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameContent(
    uiState: AddGameUiState,
    addedGameIds: Set<Long>,
    snackbarHostState: SnackbarHostState,
    onSearch: (String) -> Unit,
    onAdd: (IgdbGame) -> Unit,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.add_game_title),
                onNavigate = onNavigate,
                showSearchToggle = false
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text(stringResource(R.string.add_game_search_hint)) },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { onSearch(searchQuery) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                },
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is AddGameUiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.add_game_idle))
                    }
                }
                is AddGameUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is AddGameUiState.Empty -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.add_game_empty))
                    }
                }
                is AddGameUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is AddGameUiState.Results -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.games) { game ->
                            GameSearchResultItem(
                                game = game,
                                isAdded = addedGameIds.contains(game.id),
                                onAdd = { onAdd(game) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameSearchResultItem(
    game: IgdbGame,
    isAdded: Boolean,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = game.cover?.url?.replace("t_thumb", "t_cover_big")?.let { "https:$it" },
                contentDescription = null,
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = game.name, style = MaterialTheme.typography.titleMedium)
                game.firstReleaseDate?.let {
                    Text(
                        text = "Released: ${formatYear(it)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (isAdded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Added",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            } else {
                Button(onClick = onAdd) {
                    Text("Add")
                }
            }
        }
    }
}

private fun formatYear(timestamp: Long): String {
    val date = java.util.Date(timestamp * 1000L)
    val calendar = java.util.Calendar.getInstance()
    calendar.time = date
    return calendar.get(java.util.Calendar.YEAR).toString()
}

@Preview(showBackground = true)
@Composable
fun AddGameScreenPreview() {
    PcGameCollectionTheme {
        val mockGames = listOf(
            IgdbGame(id = 1, name = "Stardew Valley", firstReleaseDate = 1456444800),
            IgdbGame(id = 2, name = "Cyberpunk 2077", firstReleaseDate = 1607558400)
        )
        AddGameContent(
            uiState = AddGameUiState.Results(mockGames),
            addedGameIds = setOf(1L),
            snackbarHostState = remember { SnackbarHostState() },
            onSearch = {},
            onAdd = {},
            onBack = {},
            onNavigate = {}
        )
    }
}
