package com.github.hagendietrich.pcgamecollection.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
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
    val wishlistGameIds by viewModel.wishlistGameIds.collectAsState()
    val selectedGameForDetail by viewModel.selectedGameForDetail.collectAsState()
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
        wishlistGameIds = wishlistGameIds,
        snackbarHostState = snackbarHostState,
        onSearch = { viewModel.searchGames(it) },
        onAdd = { viewModel.addGame(it) },
        onAddToWishlist = { viewModel.addToWishlist(it) },
        onSelectForDetail = { viewModel.selectGameForDetail(it) },
        onBack = onBack,
        onNavigate = onNavigate
    )

    selectedGameForDetail?.let { game ->
        IgdbGameDetailDialog(
            game = game,
            isAdded = addedGameIds.contains(game.id),
            isWishlisted = wishlistGameIds.contains(game.id),
            onDismiss = { viewModel.selectGameForDetail(null) },
            onAdd = {
                viewModel.addGame(game)
                viewModel.selectGameForDetail(null)
            },
            onAddToWishlist = {
                viewModel.addToWishlist(game)
                viewModel.selectGameForDetail(null)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGameContent(
    uiState: AddGameUiState,
    addedGameIds: Set<Long>,
    wishlistGameIds: Set<Long>,
    snackbarHostState: SnackbarHostState,
    onSearch: (String) -> Unit,
    onAdd: (IgdbGame) -> Unit,
    onAddToWishlist: (IgdbGame) -> Unit,
    onSelectForDetail: (IgdbGame) -> Unit,
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
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { 
                    searchQuery = it
                    if (it.isBlank()) onSearch("")
                },
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
                is AddGameUiState.AnticipatedResults -> {
                    Text(
                        text = stringResource(R.string.anticipated_games_header),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.games) { game ->
                            GameSearchResultItem(
                                game = game,
                                isAdded = addedGameIds.contains(game.id),
                                isWishlisted = wishlistGameIds.contains(game.id),
                                onAdd = { onAdd(game) },
                                onAddToWishlist = { onAddToWishlist(game) },
                                onClick = { onSelectForDetail(game) }
                            )
                        }
                    }
                }
                is AddGameUiState.Results -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.games) { game ->
                            GameSearchResultItem(
                                game = game,
                                isAdded = addedGameIds.contains(game.id),
                                isWishlisted = wishlistGameIds.contains(game.id),
                                onAdd = { onAdd(game) },
                                onAddToWishlist = { onAddToWishlist(game) },
                                onClick = { onSelectForDetail(game) }
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
    isWishlisted: Boolean,
    onAdd: () -> Unit,
    onAddToWishlist: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .height(90.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = game.cover?.url?.replace("t_thumb", "t_cover_big")?.let { "https:$it" },
                contentDescription = null,
                modifier = Modifier
                    .width(65.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                game.firstReleaseDate?.let {
                    Text(
                        text = formatYear(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
            
            Row {
                if (isWishlisted) {
                    IconButton(onClick = {}, enabled = false) {
                        Icon(Icons.Default.BookmarkAdded, contentDescription = "Wishlisted", tint = MaterialTheme.colorScheme.secondary)
                    }
                } else if (!isAdded) {
                    IconButton(onClick = onAddToWishlist) {
                        Icon(Icons.Default.Bookmark, contentDescription = "Add to Wishlist")
                    }
                }
                
                if (isAdded) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Added",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    Button(onClick = onAdd, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("Add")
                    }
                }
            }
        }
    }
}

@Composable
fun IgdbGameDetailDialog(
    game: IgdbGame,
    isAdded: Boolean,
    isWishlisted: Boolean,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onAddToWishlist: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header with Image
                Box(modifier = Modifier.height(200.dp)) {
                    AsyncImage(
                        model = game.screenshots?.firstOrNull()?.url?.replace("t_thumb", "t_screenshot_huge")?.let { "https:$it" }
                            ?: game.cover?.url?.replace("t_thumb", "t_cover_big")?.let { "https:$it" },
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                    ) {
                        Text(
                            text = game.name,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Release Date", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text(game.firstReleaseDate?.let { formatYear(it) } ?: "TBA", style = MaterialTheme.typography.bodyMedium)
                        }
                        game.aggregatedRating?.let {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Rating", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Text("${it.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Summary
                    if (!game.summary.isNullOrBlank()) {
                        Text("Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(game.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Genres
                    val genres = game.genres?.map { it.name } ?: emptyList()
                    if (genres.isNotEmpty()) {
                        Text("Genres", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(genres.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Developers
                    val devs = game.involvedCompanies?.filter { it.developer }?.mapNotNull { it.company?.name } ?: emptyList()
                    if (devs.isNotEmpty()) {
                        Text("Developers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(devs.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Close")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (!isWishlisted && !isAdded) {
                            OutlinedButton(onClick = onAddToWishlist) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Wishlist")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (!isAdded) {
                            Button(onClick = onAdd) {
                                Text("Add to Library")
                            }
                        } else {
                            Text("In Library", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(8.dp))
                        }
                    }
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
            wishlistGameIds = emptySet(),
            snackbarHostState = remember { SnackbarHostState() },
            onSearch = {},
            onAdd = {},
            onAddToWishlist = {},
            onSelectForDetail = {},
            onBack = {},
            onNavigate = {}
        )
    }
}
