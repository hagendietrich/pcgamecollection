package com.example.digitalcollectionmanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.ui.theme.DigitalCollectionManagerTheme
import com.example.digitalcollectionmanager.ui.viewmodel.GameListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    viewModel: GameListViewModel,
    onAddGame: () -> Unit
) {
    val games by viewModel.games.collectAsState()
    val columnCount by viewModel.columnCount.collectAsState()
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Collection") },
                actions = {
                    IconButton(onClick = { viewModel.setColumnCount((columnCount - 1).coerceAtLeast(2)) }) {
                        Text("-", style = MaterialTheme.typography.headlineMedium)
                    }
                    IconButton(onClick = { viewModel.setColumnCount((columnCount + 1).coerceAtMost(5)) }) {
                        Text("+", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddGame) {
                Text("+")
            }
        }
    ) { innerPadding ->
        if (games.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Your collection is empty. Tap + to add games!")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(games) { game ->
                    GameCoverItem(
                        game = game,
                        onClick = {
                            selectedGame = game
                            showBottomSheet = true
                        }
                    )
                }
            }
        }
    }

    if (showBottomSheet && selectedGame != null) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            GameDetailContent(
                game = selectedGame!!,
                onDelete = {
                    viewModel.deleteGame(selectedGame!!)
                    showBottomSheet = false
                }
            )
        }
    }
}

@Composable
fun GameCoverItem(
    game: Game,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(0.75f)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        AsyncImage(
            model = game.coverImageUrl,
            contentDescription = game.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun GameDetailContent(
    game: Game,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = game.coverImageUrl,
            contentDescription = null,
            modifier = Modifier
                .height(240.dp)
                .aspectRatio(0.75f),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = game.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Platform: ${game.platform}",
            style = MaterialTheme.typography.bodyMedium
        )
        game.releaseDate?.let {
            Text(
                text = "Released: $it",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (game.genres.isNotEmpty()) {
            Text(
                text = "Genres: ${game.genres.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Remove from Collection")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun GameListScreenPreview() {
    DigitalCollectionManagerTheme {
        val mockGames = listOf(
            Game(1, "The Witcher 3", "PC", "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg", "2015", true),
            Game(2, "Cyberpunk 2077", "PC", "https://images.igdb.com/igdb/image/upload/t_cover_big/co2mdf.jpg", "2020", true),
            Game(3, "Stardew Valley", "PC", "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg", "2016", true),
            Game(4, "Hades", "PC", "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg", "2020", true)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(mockGames) { game ->
                GameCoverItem(game = game, onClick = {})
            }
        }
    }
}
