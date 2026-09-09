package com.github.hagendietrich.pcgamecollection.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.data.model.PlatformPrice
import com.github.hagendietrich.pcgamecollection.data.model.WishlistGame
import com.github.hagendietrich.pcgamecollection.ui.components.AppTopBar
import com.github.hagendietrich.pcgamecollection.ui.theme.PcGameCollectionTheme
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.WishlistSearchUiState
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.WishlistSortOrder
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.WishlistViewModel
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel,
    onNavigate: (String) -> Unit
) {
    val wishlistGames by viewModel.wishlistGames.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val selectedGameForDetail by viewModel.selectedGameForDetail.collectAsState()
    val libraryIgdbIds by viewModel.libraryIgdbIds.collectAsState()
    val searchUiState by viewModel.searchUiState.collectAsState()
    val isAdding by viewModel.isAdding.collectAsState()
    val refreshingIds by viewModel.refreshingIds.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    var showAddDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var gameToDelete by remember { mutableStateOf<WishlistGame?>(null) }
    var expandedIds by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { (resId, arg) ->
            snackbarHostState.showSnackbar(
                if (arg != null) context.getString(resId, arg) else context.getString(resId)
            )
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AppTopBar(
                title = stringResource(R.string.wishlist_title),
                onNavigate = onNavigate,
                showSearchToggle = false,
                actions = {
                    if (wishlistGames.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.sort_title))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_added_desc)) },
                                    onClick = {
                                        viewModel.setSortOrder(WishlistSortOrder.DATE_ADDED_DESC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == WishlistSortOrder.DATE_ADDED_DESC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_added_asc)) },
                                    onClick = {
                                        viewModel.setSortOrder(WishlistSortOrder.DATE_ADDED_ASC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == WishlistSortOrder.DATE_ADDED_ASC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_desc)) },
                                    onClick = {
                                        viewModel.setSortOrder(WishlistSortOrder.RELEASE_DATE_DESC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == WishlistSortOrder.RELEASE_DATE_DESC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_asc)) },
                                    onClick = {
                                        viewModel.setSortOrder(WishlistSortOrder.RELEASE_DATE_ASC)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == WishlistSortOrder.RELEASE_DATE_ASC) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_price_asc)) },
                                    onClick = {
                                        viewModel.setSortOrder(WishlistSortOrder.BEST_PRICE)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (sortOrder == WishlistSortOrder.BEST_PRICE) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.refreshAllPrices() }) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.wishlist_refresh_prices))
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.clearSearchResults()
                showAddDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isAdding) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (wishlistGames.isEmpty()) {
                Text(
                    text = stringResource(R.string.wishlist_empty),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(wishlistGames, key = { it.id }) { game ->
                        WishlistItemCard(
                            game = game,
                            isExpanded = expandedIds.contains(game.id),
                            isRefreshing = refreshingIds.contains(game.id),
                            onClick = {
                                expandedIds =
                                    if (expandedIds.contains(game.id)) expandedIds - game.id
                                    else expandedIds + game.id
                            },
                            onRefresh = { viewModel.refreshPrices(game) },
                            onDelete = { gameToDelete = game },
                            onShowDetail = { viewModel.selectGameForDetail(game.igdbId) }
                        )
                    }
                }
            }
        }
    }

    selectedGameForDetail?.let { game ->
        IgdbGameDetailDialog(
            game = game,
            isAdded = libraryIgdbIds.contains(game.id),
            isWishlisted = true,
            onDismiss = { viewModel.selectGameForDetail(null) },
            onAdd = {
                viewModel.addGameToLibrary(game)
                viewModel.selectGameForDetail(null)
            },
            onAddToWishlist = {
                viewModel.selectGameForDetail(null)
            }
        )
    }

    if (showAddDialog) {
        AddToWishlistDialog(
            uiState = searchUiState,
            isAdding = isAdding,
            onSearch = { viewModel.searchGames(it) },
            onSelect = { igdbGame ->
                showAddDialog = false
                viewModel.addGame(igdbGame)
            },
            onDismiss = {
                showAddDialog = false
                viewModel.clearSearchResults()
            }
        )
    }

    if (gameToDelete != null) {
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            title = { Text(stringResource(R.string.wishlist_remove_title)) },
            text = { Text(stringResource(R.string.wishlist_remove_text, gameToDelete!!.title)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGame(gameToDelete!!)
                    gameToDelete = null
                }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { gameToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun WishlistItemCard(
    game: WishlistGame,
    isExpanded: Boolean,
    isRefreshing: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onShowDetail: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clickable { onClick() }
                .padding(8.dp)
                .height(96.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = game.coverImageUrl,
                contentDescription = game.title,
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clickable { onShowDetail() },
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))

                val year = game.releaseDate?.let {
                    java.time.Instant.ofEpochSecond(it)
                        .atZone(java.time.ZoneId.systemDefault()).year
                }

                val lowest = game.lowestPrice()
                val storeCountRes = if (game.platformPrices.size == 1)
                    R.string.wishlist_store_count_one else R.string.wishlist_store_count_many
                Text(
                    text = (year?.let { "$it · " } ?: "") +
                            stringResource(storeCountRes, game.platformPrices.size) +
                            (lowest?.let { " · ${formatEurPrice(it.price)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isRefreshing) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        if (isExpanded) {
            HorizontalDivider()

            game.platformPrices.forEach { platformPrice ->
                PlatformPriceRow(platformPrice = platformPrice)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.wishlist_refresh_prices))
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun PlatformPriceRow(platformPrice: PlatformPrice) {
    val context = LocalContext.current
    val locales = androidx.compose.ui.platform.LocalConfiguration.current.locales
    val updatedText = remember(platformPrice.lastUpdated, locales) {
        DateFormat.getDateInstance(DateFormat.SHORT, locales[0])
            .format(Date(platformPrice.lastUpdated))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = platformPrice.storeUrl.isNotBlank()) {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(platformPrice.storeUrl)))
                }
            }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = platformPrice.platform,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (platformPrice.price > 0.0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = formatEurPrice(platformPrice.price),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (platformPrice.isOnSale && platformPrice.originalPrice != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = formatEurPrice(platformPrice.originalPrice!!),
                            style = MaterialTheme.typography.bodySmall.copy(
                                textDecoration = TextDecoration.LineThrough
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SALE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.wishlist_price_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = updatedText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (platformPrice.storeUrl.isNotBlank()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = platformPrice.storeUrl,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun AddToWishlistDialog(
    uiState: WishlistSearchUiState,
    isAdding: Boolean,
    onSearch: (String) -> Unit,
    onSelect: (IgdbGame) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_game_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    label = { Text(stringResource(R.string.add_game_search_hint)) },
                    singleLine = true,
                    enabled = !isAdding,
                    trailingIcon = {
                        IconButton(onClick = { onSearch(searchText) }, enabled = !isAdding) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                when (val state = uiState) {
                    is WishlistSearchUiState.Idle -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.add_game_idle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is WishlistSearchUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is WishlistSearchUiState.Empty -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.add_game_empty))
                        }
                    }
                    is WishlistSearchUiState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                            Text(state.message, color = MaterialTheme.colorScheme.error)
                        }
                    }
                    is WishlistSearchUiState.Results -> {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(state.games, key = { it.id }) { candidate ->
                                val year = candidate.firstReleaseDate?.let {
                                    java.time.Instant.ofEpochSecond(it)
                                        .atZone(java.time.ZoneId.systemDefault()).year
                                }
                                ListItem(
                                    headlineContent = { Text(candidate.name) },
                                    supportingContent = { Text(year?.toString() ?: "") },
                                    leadingContent = {
                                        AsyncImage(
                                            model = candidate.cover?.url
                                                ?.replace("t_thumb", "t_cover_small")
                                                ?.let { "https:$it" },
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                    },
                                    modifier = Modifier.clickable(enabled = !isAdding) { onSelect(candidate) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isAdding) {
                Text("Cancel")
            }
        }
    )
}

/** Formats a price value as Euro currency using German locale (e.g. "14,99 €"). */
internal fun formatEurPrice(price: Double): String {
    return java.text.NumberFormat.getCurrencyInstance(Locale.GERMANY).format(price)
}

@Preview(showBackground = true)
@Composable
fun WishlistScreenPreview() {
    PcGameCollectionTheme {
        val mockGames = listOf(
            WishlistGame(
                id = 1,
                title = "The Witcher 3: Wild Hunt",
                coverImageUrl = "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg",
                platformPrices = listOf(
                    PlatformPrice(platform = "Steam", storeUrl = "https://store.steampowered.com/app/292030", price = 14.99, isOnSale = true, originalPrice = 49.99),
                    PlatformPrice(platform = "GOG", storeUrl = "https://www.gog.com/en/game/the_witcher_3_wild_hunt", price = 0.0)
                )
            ),
            WishlistGame(
                id = 2,
                title = "Cyberpunk 2077",
                platformPrices = listOf(
                    PlatformPrice(platform = "Steam", storeUrl = "https://store.steampowered.com/app/1091500", price = 59.99)
                )
            )
        )

        var expandedIds by remember { mutableStateOf(setOf(1)) }

        Scaffold { innerPadding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(mockGames, key = { it.id }) { game ->
                    WishlistItemCard(
                        game = game,
                        isExpanded = expandedIds.contains(game.id),
                        isRefreshing = false,
                        onClick = {
                            expandedIds =
                                if (expandedIds.contains(game.id)) expandedIds - game.id
                                else expandedIds + game.id
                        },
                        onRefresh = {},
                        onDelete = {},
                        onShowDetail = {}
                    )
                }
            }
        }
    }
}
