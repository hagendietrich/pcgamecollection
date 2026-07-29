package com.example.digitalcollectionmanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.digitalcollectionmanager.R
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.model.CompletionStatus
import com.example.digitalcollectionmanager.data.model.Game
import com.example.digitalcollectionmanager.data.model.GroupingType
import com.example.digitalcollectionmanager.data.model.SortOrder
import com.example.digitalcollectionmanager.ui.components.AppTopBar
import com.example.digitalcollectionmanager.ui.theme.DigitalCollectionManagerTheme
import com.example.digitalcollectionmanager.ui.viewmodel.GameListViewModel
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GameListScreen(
    viewModel: GameListViewModel,
    onNavigate: (String) -> Unit,
    onAddGame: () -> Unit,
    onShowFullDetails: (Int) -> Unit
) {
    val allGames by viewModel.allGames.collectAsState()
    val groupedGames by viewModel.groupedGames.collectAsState()
    val columnCount by viewModel.columnCount.collectAsState()
    val selectedGameIds by viewModel.selectedGameIds.collectAsState()
    val isMultiSelectMode by viewModel.isMultiSelectMode.collectAsState()
    val reMatchResults by viewModel.reMatchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val libraryFilters by viewModel.libraryFilters.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()
    val allGenres by viewModel.allGenres.collectAsState()
    
    var selectedGame by remember { mutableStateOf<Game?>(null) }
    
    // Keep selectedGame in sync with the latest data from the repository
    LaunchedEffect(allGames) {
        selectedGame?.let { current ->
            val updated = allGames.find { it.id == current.id }
            if (updated != null) {
                selectedGame = updated
            }
        }
    }

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupingMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var filterCategoryToEdit by remember { mutableStateOf<String?>(null) }
    var showReMatchDialog by remember { mutableStateOf(false) }

    // State to track collapsed groups
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                MultiSelectTopBar(
                    selectedCount = selectedGameIds.size,
                    onClose = { viewModel.clearSelection() },
                    onUpdateStatus = { viewModel.updateSelectedStatus(it) },
                    onAddLabel = { viewModel.addLabelToSelected(it) },
                    onRemoveLabel = { viewModel.removeLabelFromSelected(it) },
                    viewModel = viewModel
                )
            } else {
                AppTopBar(
                    title = stringResource(R.string.library_title),
                    onNavigate = onNavigate,
                    isSearchActive = true,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    showSearchToggle = false,
                    actions = {
                        // Grouping Button
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showGroupingMenu = true }) {
                                Icon(Icons.Default.GroupWork, contentDescription = "Group")
                            }
                            DropdownMenu(
                                expanded = showGroupingMenu,
                                onDismissRequest = { showGroupingMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("No Grouping") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.NONE); showGroupingMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Group by Status") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.STATUS); showGroupingMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Group by Label") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.LABEL); showGroupingMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Group by Platform") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.PLATFORM); showGroupingMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Group by Genre") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.GENRE); showGroupingMenu =
                                        false
                                    }
                                )
                            }
                        }

                        // Filter Button
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter")
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false }
                            ) {
                                val categories = listOf("Mode", "Platform", "Status", "Labels", "Genre")
                                categories.forEach { category ->
                                    val isActive = libraryFilters.isCategoryActive(category)
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(32.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isActive) {
                                                        IconButton(
                                                            onClick = { 
                                                                viewModel.clearCategoryFilter(category)
                                                            }
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Check,
                                                                contentDescription = "Clear",
                                                                modifier = Modifier.size(18.dp),
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = category,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        },
                                        onClick = {
                                            filterCategoryToEdit = category
                                            showFilterMenu = false
                                        }
                                    )
                                }
                                if (libraryFilters.hasAnyActiveFilters()) {
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Clear All Filters", color = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            viewModel.clearAllFilters()
                                            showFilterMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Sorting Button
                        Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.sort_title)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_title)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.TITLE_ASC); showSortMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_desc)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.RELEASE_DATE_DESC); showSortMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_asc)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.RELEASE_DATE_ASC); showSortMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_playtime_desc)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.PLAYTIME_DESC); showSortMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_playtime_asc)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.PLAYTIME_ASC); showSortMenu =
                                        false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = {
                            viewModel.setColumnCount(
                                (columnCount - 1).coerceAtLeast(
                                    1
                                )
                            )
                        }) {
                            Icon(Icons.Default.ZoomIn, contentDescription = "Bigger Covers")
                        }
                        IconButton(onClick = {
                            viewModel.setColumnCount(
                                (columnCount + 1).coerceAtMost(
                                    10
                                )
                            )
                        }) {
                            Icon(Icons.Default.ZoomOut, contentDescription = "Smaller Covers")
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isMultiSelectMode) {
                FloatingActionButton(onClick = onAddGame) {
                    Text("+")
                }
            }
        }
    ) { innerPadding ->
        if (groupedGames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.library_empty))
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
                groupedGames.forEach { (groupName, gamesInGroup) ->
                    if (groupName.isNotEmpty()) {
                        val isCollapsed = collapsedGroups[groupName] ?: false
                        item(span = { GridItemSpan(columnCount) }) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { collapsedGroups[groupName] = !isCollapsed }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$groupName (${gamesInGroup.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Icon(
                                        imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                        contentDescription = if (isCollapsed) "Expand" else "Collapse"
                                    )
                                }
                            }
                        }
                        if (!isCollapsed) {
                            items(gamesInGroup) { game ->
                                GameCoverItem(
                                    game = game,
                                    isSelected = selectedGameIds.contains(game.id),
                                    isMultiSelect = isMultiSelectMode,
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            viewModel.toggleSelection(game.id)
                                        } else {
                                            selectedGame = game
                                            showBottomSheet = true
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.toggleSelection(game.id)
                                    }
                                )
                            }
                        }
                    } else {
                        items(gamesInGroup) { game ->
                            GameCoverItem(
                                game = game,
                                isSelected = selectedGameIds.contains(game.id),
                                isMultiSelect = isMultiSelectMode,
                                onClick = {
                                    if (isMultiSelectMode) {
                                        viewModel.toggleSelection(game.id)
                                    } else {
                                        selectedGame = game
                                        showBottomSheet = true
                                    }
                                },
                                onLongClick = {
                                    viewModel.toggleSelection(game.id)
                                }
                            )
                        }
                    }
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
                viewModel = viewModel,
                onUpdate = { updatedGame ->
                    viewModel.updateGame(updatedGame)
                    selectedGame = updatedGame
                },
                onDelete = {
                    viewModel.deleteGame(selectedGame!!)
                    showBottomSheet = false
                },
                onReMatchClick = {
                    viewModel.searchForReMatch(selectedGame!!.title)
                    showReMatchDialog = true
                },
                onShowFullDetails = { 
                    showBottomSheet = false
                    onShowFullDetails(it)
                }
            )
        }
    }

    if (showReMatchDialog && selectedGame != null) {
        ReMatchDialog(
            initialTitle = selectedGame!!.title,
            candidates = reMatchResults,
            onSearch = { viewModel.searchForReMatch(it) },
            onSelect = { candidate ->
                viewModel.applyReMatch(selectedGame!!.id, candidate)
                showReMatchDialog = false
                showBottomSheet = false // Close detail sheet too
            },
            onDismiss = {
                viewModel.clearReMatchResults()
                showReMatchDialog = false
            }
        )
    }

    if (filterCategoryToEdit != null) {
        val category = filterCategoryToEdit!!
        val options = when (category) {
            "Mode" -> listOf("Singleplayer", "Multiplayer", "Co-op")
            "Platform" -> allGames.flatMap { it.platforms }.distinct().sorted()
            "Status" -> CompletionStatus.entries.map { it.name }
            "Labels" -> allLabels
            "Genre" -> allGenres
            else -> emptyList()
        }

        FilterSelectionDialog(
            categoryName = category,
            options = options,
            currentFilters = when (category) {
                "Mode" -> libraryFilters.modes
                "Platform" -> libraryFilters.platforms
                "Status" -> libraryFilters.statuses
                "Labels" -> libraryFilters.labels
                "Genre" -> libraryFilters.genres
                else -> emptyMap()
            },
            onToggle = { item -> viewModel.toggleFilter(category, item) },
            onDismiss = { filterCategoryToEdit = null }
        )
    }
}

@Composable
fun FilterSelectionDialog(
    categoryName: String,
    options: List<String>,
    currentFilters: Map<String, com.example.digitalcollectionmanager.data.model.FilterType>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by $categoryName") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(options) { option ->
                    val type = currentFilters[option] ?: com.example.digitalcollectionmanager.data.model.FilterType.NONE
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(option) }
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when (type) {
                                com.example.digitalcollectionmanager.data.model.FilterType.NONE -> Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null)
                                com.example.digitalcollectionmanager.data.model.FilterType.INCLUDE -> Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                com.example.digitalcollectionmanager.data.model.FilterType.EXCLUDE -> Icon(Icons.Default.RemoveCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Text(option, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCoverItem(
    game: Game,
    isSelected: Boolean,
    isMultiSelect: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .aspectRatio(0.75f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = MaterialTheme.shapes.extraSmall,
        border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = game.coverImageUrl,
                contentDescription = game.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (isSelected) {
                Surface(
                    color = Color.Black.copy(alpha = 0.3f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.padding(4.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (isMultiSelect) {
                Box(contentAlignment = Alignment.TopEnd, modifier = Modifier.padding(4.dp)) {
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.White.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color.Black),
                        modifier = Modifier.size(24.dp)
                    ) {}
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiSelectTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onUpdateStatus: (CompletionStatus) -> Unit,
    onAddLabel: (String) -> Unit,
    onRemoveLabel: (String) -> Unit,
    viewModel: GameListViewModel
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showLabelManager by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("$selectedCount Selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cancel")
            }
        },
        actions = {
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                TextButton(onClick = { showStatusMenu = true }) {
                    Text("Status")
                }
                DropdownMenu(expanded = showStatusMenu, onDismissRequest = { showStatusMenu = false }) {
                    CompletionStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            onClick = { onUpdateStatus(status); showStatusMenu = false }
                        )
                    }
                }
            }
            TextButton(onClick = { showLabelManager = true }) {
                Text("Labels")
            }
        }
    )

    if (showLabelManager) {
        BulkLabelManagerDialog(
            viewModel = viewModel,
            onAdd = onAddLabel,
            onRemove = onRemoveLabel,
            onDismiss = { showLabelManager = false }
        )
    }
}

@Composable
fun BulkLabelManagerDialog(
    viewModel: GameListViewModel,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds by viewModel.selectedGameIds.collectAsState()
    var newLabelText by remember { mutableStateOf("") }

    // Find labels present in ANY of the selected games
    val groupedGames by viewModel.groupedGames.collectAsState()
    val commonLabels = remember(selectedIds, groupedGames) {
        groupedGames.values.flatten().filter { it.id in selectedIds }.flatMap { it.labels }.toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Labels") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = newLabelText,
                    onValueChange = { newLabelText = it },
                    label = { Text("Add New Label") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { 
                            onAdd(newLabelText)
                            newLabelText = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                Text("Existing Labels in Selection:", style = MaterialTheme.typography.labelMedium)
                
                if (commonLabels.isEmpty()) {
                    Text("No labels found in selected games.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        items(commonLabels.toList()) { label ->
                            ListItem(
                                headlineContent = { Text(label) },
                                trailingContent = {
                                    IconButton(onClick = { onRemove(label) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameDetailContent(
    game: Game,
    viewModel: GameListViewModel,
    onUpdate: (Game) -> Unit,
    onDelete: () -> Unit,
    onReMatchClick: () -> Unit,
    onShowFullDetails: (Int) -> Unit
) {
    var showPlaytimePicker by remember { mutableStateOf(false) }
    var showAddLabelDialog by remember { mutableStateOf(false) }
    var showAddGenreDialog by remember { mutableStateOf(false) }
    var showReleaseDatePicker by remember { mutableStateOf(false) }
    var showEditModeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(game.id) {
        viewModel.enrichGame(game.id)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 48.dp),
                textAlign = TextAlign.Center
            )
            IconButton(
                onClick = onReMatchClick,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Re-match IGDB Metadata")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        
        AsyncImage(
            model = game.coverImageUrl,
            contentDescription = null,
            modifier = Modifier
                .height(240.dp)
                .aspectRatio(0.75f)
                .clickable { onShowFullDetails(game.id) },
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.detail_platform, game.platforms.joinToString(", ")),
            style = MaterialTheme.typography.bodyMedium
        )
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.detail_released, ""),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = game.releaseDate ?: "Add Release Date",
                style = MaterialTheme.typography.bodyMedium.copy(
                    textDecoration = if (game.releaseDate == null) androidx.compose.ui.text.style.TextDecoration.Underline else null
                ),
                modifier = Modifier.combinedClickable(
                    onClick = { if (game.releaseDate == null) showReleaseDatePicker = true },
                    onLongClick = { showReleaseDatePicker = true }
                )
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Status: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                TextButton(onClick = { expanded = true }) {
                    Text(game.completionStatus.name)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    CompletionStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.name) },
                            onClick = {
                                onUpdate(game.copy(completionStatus = status))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // Playtime Row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Playtime: ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            val hours = game.playtimeMinutes / 60
            val minutes = game.playtimeMinutes % 60
            TextButton(onClick = { showPlaytimePicker = true }) {
                Text("${hours}h ${minutes}m")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Mode
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.detail_mode), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { showEditModeDialog = true }
                ) {
                    Text(
                        text = " + Add ",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                game.gameModes.forEach { mode ->
                    ManageableChip(
                        text = mode,
                        onDelete = { 
                            viewModel.updateGameModes(game.id, game.gameModes - mode)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Genres
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Genres:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { showAddGenreDialog = true }
                ) {
                    Text(
                        text = " + Add ",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                game.genres.forEach { genre ->
                    ManageableChip(
                        text = genre,
                        onDelete = { viewModel.removeGenreFromGame(game.id, genre) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Labels
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Labels:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.clickable { showAddLabelDialog = true }
                ) {
                    Text(
                        text = " + Add ",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                game.labels.forEach { label ->
                    ManageableChip(
                        text = label,
                        onDelete = { viewModel.removeLabelFromGame(game.id, label) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onDelete,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.detail_remove))
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showPlaytimePicker) {
        PlaytimePickerDialog(
            initialMinutes = game.playtimes["Manual"] ?: 0,
            onDismiss = { showPlaytimePicker = false },
            onConfirm = { newMinutes ->
                // When manually updating playtime, we store it under a "Manual" source in our internal map
                val updatedPlaytimes = game.playtimes.toMutableMap()
                updatedPlaytimes["Manual"] = newMinutes
                val totalPlaytime = updatedPlaytimes.values.sum()
                
                onUpdate(game.copy(
                    playtimes = updatedPlaytimes,
                    playtimeMinutes = totalPlaytime
                ))
                showPlaytimePicker = false
            }
        )
    }

    if (showReleaseDatePicker) {
        SetReleaseDateDialog(
            initialDate = game.releaseDate ?: "",
            onConfirm = { 
                viewModel.updateReleaseDate(game.id, it)
                showReleaseDatePicker = false 
            },
            onDismiss = { showReleaseDatePicker = false }
        )
    }

    if (showAddLabelDialog) {
        val allLabels by viewModel.allLabels.collectAsState()
        AddTagDialog(
            title = "Add Label",
            suggestions = allLabels,
            onConfirm = { viewModel.addLabelToGame(game.id, it); showAddLabelDialog = false },
            onDismiss = { showAddLabelDialog = false }
        )
    }

    if (showAddGenreDialog) {
        val allGenres by viewModel.allGenres.collectAsState()
        AddTagDialog(
            title = "Add Genre",
            suggestions = allGenres,
            onConfirm = { viewModel.addGenreToGame(game.id, it); showAddGenreDialog = false },
            onDismiss = { showAddGenreDialog = false }
        )
    }

    if (showEditModeDialog) {
        EditModeDialog(
            currentModes = game.gameModes,
            onConfirm = { 
                viewModel.updateGameModes(game.id, it)
                showEditModeDialog = false 
            },
            onDismiss = { showEditModeDialog = false }
        )
    }
}

@Composable
fun EditModeDialog(
    currentModes: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf("Singleplayer", "Multiplayer", "Co-op")
    val selectedModes = remember { mutableStateListOf<String>().apply { addAll(currentModes) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_mode_edit)) },
        text = {
            Column {
                options.forEach { mode ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically, 
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { 
                                if (selectedModes.contains(mode)) selectedModes.remove(mode) else selectedModes.add(mode)
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selectedModes.contains(mode), 
                            onCheckedChange = { 
                                if (it) selectedModes.add(mode) else selectedModes.remove(mode)
                            }
                        )
                        Text(mode, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedModes.toList()) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageableChip(
    text: String,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .combinedClickable(
                onClick = {},
                onLongClick = { showDeleteConfirm = true }
            ),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove \"$text\"?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AddTagDialog(
    title: String,
    suggestions: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Existing:", style = MaterialTheme.typography.labelSmall)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            AssistChip(
                                onClick = { text = suggestion },
                                label = { Text(suggestion) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PlaytimePickerDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var hoursStr by remember { mutableStateOf((initialMinutes / 60).toString()) }
    var minutesStr by remember { mutableStateOf((initialMinutes % 60).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Offline Playtime") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = hoursStr,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                            hoursStr = newValue
                        }
                    },
                    label = { Text("Hours") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                )
                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                OutlinedTextField(
                    value = minutesStr,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || (newValue.all { it.isDigit() } && (newValue.toIntOrNull() ?: 0) < 60)) {
                            minutesStr = newValue
                        }
                    },
                    label = { Text("Minutes") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val h = hoursStr.toIntOrNull() ?: 0
                    val m = minutesStr.toIntOrNull() ?: 0
                    onConfirm(h * 60 + m)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SetReleaseDateDialog(
    initialDate: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var dateText by remember { mutableStateOf(initialDate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Release Date") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter date in YYYY-MM-DD format:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Release Date") },
                    placeholder = { Text("e.g. 2024-05-20") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(dateText) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReMatchDialog(
    initialTitle: String,
    candidates: List<IgdbGame>,
    onSearch: (String) -> Unit,
    onSelect: (IgdbGame) -> Unit,
    onDismiss: () -> Unit
) {
    var searchText by remember { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Re-match IGDB Metadata") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search Title") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    IconButton(onClick = { onSearch(searchText) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (candidates.isEmpty()) {
                    Text("No results. Try refining your search.", style = MaterialTheme.typography.bodySmall)
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(candidates) { candidate ->
                            val year = candidate.firstReleaseDate?.let { 
                                java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).year 
                            }
                            
                            ListItem(
                                headlineContent = { Text(candidate.name) },
                                supportingContent = { Text(year?.toString() ?: "Unknown Year") },
                                leadingContent = {
                                    AsyncImage(
                                        model = candidate.cover?.url?.let { "https:" + it.replace("t_thumb", "t_cover_small") },
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        contentScale = ContentScale.Crop
                                    )
                                },
                                modifier = Modifier.clickable { onSelect(candidate) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun GameListScreenPreview() {
    DigitalCollectionManagerTheme {
        val mockGames = listOf(
            Game(1, "The Witcher 3", listOf("PC"), "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg", "2015", true),
            Game(2, "Cyberpunk 2077", listOf("PC"), "https://images.igdb.com/igdb/image/upload/t_cover_big/co2mdf.jpg", "2020", true),
            Game(3, "Stardew Valley", listOf("PC"), "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg", "2016", true),
            Game(4, "Hades", listOf("PC"), "https://images.igdb.com/igdb/image/upload/t_cover_big/co1r8v.jpg", "2020", true)
        )
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(mockGames) { game ->
                GameCoverItem(game = game, isSelected = false, isMultiSelect = false, onClick = {}, onLongClick = {})
            }
        }
    }
}
