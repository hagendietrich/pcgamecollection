package com.github.hagendietrich.pcgamecollection.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.data.model.GroupingType
import com.github.hagendietrich.pcgamecollection.data.model.SortOrder
import com.github.hagendietrich.pcgamecollection.ui.components.AppTopBar
import com.github.hagendietrich.pcgamecollection.ui.components.ManageableChip
import com.github.hagendietrich.pcgamecollection.ui.theme.PcGameCollectionTheme
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.GameListViewModel

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
    val searchQuery by viewModel.searchQuery.collectAsState()
    val libraryFilters by viewModel.libraryFilters.collectAsState()
    val allLabels by viewModel.allLabels.collectAsState()
    val allGenres by viewModel.allGenres.collectAsState()
    
    val collapsedGroups by viewModel.collapsedGroups.collectAsState()
    val resources = LocalContext.current.resources
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(
        initialFirstVisibleItemIndex = viewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )

    // Sync scroll position to ViewModel
    LaunchedEffect(gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset) {
        viewModel.updateScrollPosition(
            gridState.firstVisibleItemIndex,
            gridState.firstVisibleItemScrollOffset
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.events.collect { stringResId ->
            val message = resources.getString(stringResId)
            snackbarHostState.showSnackbar(message)
        }
    }

    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupingMenu by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var filterCategoryToEdit by remember { mutableStateOf<String?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                val displayedCount = groupedGames.values.flatten().distinctBy { it.id }.size
                AppTopBar(
                    title = stringResource(R.string.library_title),
                    onNavigate = onNavigate,
                    onSaveView = { viewModel.saveViewSnapshot() },
                    onLoadView = { viewModel.restoreViewSnapshot() },
                    isSearchActive = true,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    placeholderText = "Search $displayedCount...",
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
                                    text = { Text("Group by Series") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.SERIES); showGroupingMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Group by Franchise") },
                                    onClick = {
                                        viewModel.setGroupingType(GroupingType.FRANCHISE); showGroupingMenu =
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
                                    text = { Text(stringResource(R.string.sort_by_date_added_desc)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.DATE_ADDED_DESC); showSortMenu =
                                        false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date_added_asc)) },
                                    onClick = {
                                        viewModel.setSortOrder(SortOrder.DATE_ADDED_ASC); showSortMenu =
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
                                    20
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
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                groupedGames.forEach { (groupName, gamesInGroup) ->
                    if (groupName.isNotEmpty()) {
                        val isCollapsed = collapsedGroups.contains(groupName)
                        item(span = { GridItemSpan(columnCount) }) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleGroupCollapsed(groupName) }
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
                                            onShowFullDetails(game.id)
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
                                        onShowFullDetails(game.id)
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
    currentFilters: Map<String, com.github.hagendietrich.pcgamecollection.data.model.FilterType>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter by $categoryName") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                items(options) { option ->
                    val type = currentFilters[option] ?: com.github.hagendietrich.pcgamecollection.data.model.FilterType.NONE
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
                                com.github.hagendietrich.pcgamecollection.data.model.FilterType.NONE -> Icon(Icons.Default.CheckBoxOutlineBlank, contentDescription = null)
                                com.github.hagendietrich.pcgamecollection.data.model.FilterType.INCLUDE -> Icon(Icons.Default.AddCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                com.github.hagendietrich.pcgamecollection.data.model.FilterType.EXCLUDE -> Icon(Icons.Default.RemoveCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showStatusMenu by remember { mutableStateOf(false) }
    var showLabelManager by remember { mutableStateOf(false) }
    var showGenreManager by remember { mutableStateOf(false) }
    var showPlatformManager by remember { mutableStateOf(false) }
    var showModeManager by remember { mutableStateOf(false) }

    Surface(tonalElevation = 3.dp) {
        Column {
            TopAppBar(
                title = { Text("$selectedCount Selected") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel")
                    }
                },
                actions = {
                    Button(
                        onClick = { showDeleteConfirmation = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove All")
                    }
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    TextButton(onClick = { showStatusMenu = true }) {
                        Text("Status")
                    }
                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }) {
                        CompletionStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.name) },
                                onClick = { onUpdateStatus(status); showStatusMenu = false }
                            )
                        }
                    }
                }
                TextButton(onClick = { showLabelManager = true }) {
                    Text("Label")
                }
                TextButton(onClick = { showGenreManager = true }) {
                    Text("Genre")
                }
                TextButton(onClick = { showPlatformManager = true }) {
                    Text("Platform")
                }
                TextButton(onClick = { showModeManager = true }) {
                    Text("Mode")
                }
            }
        }
    }

    if (showLabelManager) {
        val allLabels by viewModel.allLabels.collectAsState()
        BulkTagManagerDialog(
            title = "Manage Labels",
            viewModel = viewModel,
            allTags = allLabels,
            getTagsForGame = { it.labels },
            onAdd = onAddLabel,
            onRemove = onRemoveLabel,
            onDismiss = { showLabelManager = false }
        )
    }

    if (showGenreManager) {
        val allGenres by viewModel.allGenres.collectAsState()
        BulkTagManagerDialog(
            title = "Manage Genres",
            viewModel = viewModel,
            allTags = allGenres,
            getTagsForGame = { it.genres },
            onAdd = { viewModel.addGenreToSelected(it) },
            onRemove = { viewModel.removeGenreFromSelected(it) },
            onDismiss = { showGenreManager = false }
        )
    }

    if (showPlatformManager) {
        val allPlatforms by viewModel.allPlatforms.collectAsState()
        BulkTagManagerDialog(
            title = "Manage Platforms",
            viewModel = viewModel,
            allTags = allPlatforms,
            getTagsForGame = { it.platforms },
            onAdd = { viewModel.addPlatformToSelected(it) },
            onRemove = { viewModel.removePlatformFromSelected(it) },
            onDismiss = { showPlatformManager = false }
        )
    }

    if (showModeManager) {
        BulkModeManagerDialog(
            viewModel = viewModel,
            onConfirm = { viewModel.updateSelectedGameModes(it) },
            onDismiss = { showModeManager = false }
        )
    }

    if (showDeleteConfirmation) {
        var addToIgnoreList by remember { mutableStateOf(false) }
        val selectedIds by viewModel.selectedGameIds.collectAsState()
        val allGames by viewModel.allGames.collectAsState()
        val hasOnlinePlatform = remember(selectedIds, allGames) {
            allGames.filter { it.id in selectedIds }
                .any { game -> game.platforms.any { it == "Steam" || it == "GOG" || it == "Epic" || it == "Ubisoft" || it == "Battle.net" } }
        }

        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Games") },
            text = {
                Column {
                    Text("Are you sure you want to delete all $selectedCount selected games? This cannot be undone.")
                    if (hasOnlinePlatform) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = addToIgnoreList,
                                onCheckedChange = { addToIgnoreList = it }
                            )
                            Text(
                                "Add to Ignore List (prevents future sync)",
                                modifier = Modifier.padding(start = 8.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedGames(addToIgnoreList)
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BulkTagManagerDialog(
    title: String,
    viewModel: GameListViewModel,
    allTags: List<String>,
    getTagsForGame: (Game) -> List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds by viewModel.selectedGameIds.collectAsState()
    var newTagText by remember { mutableStateOf("") }

    // Find tags present in ANY of the selected games
    val groupedGames by viewModel.groupedGames.collectAsState()
    val commonTags = remember(selectedIds, groupedGames) {
        groupedGames.values.flatten().filter { it.id in selectedIds }.flatMap { getTagsForGame(it) }.toSet()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("Add New") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { 
                            onAdd(newTagText)
                            newTagText = ""
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                )

                if (allTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Suggestions:", style = MaterialTheme.typography.labelSmall)
                    OptInFlowRowForBulk(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp).verticalScroll(rememberScrollState())
                    ) {
                        allTags.forEach { tag ->
                            AssistChip(
                                onClick = { newTagText = tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Existing in Selection:", style = MaterialTheme.typography.labelMedium)
                
                if (commonTags.isEmpty()) {
                    Text("No entries found in selected games.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        items(commonTags.toList().sorted()) { tag ->
                            ListItem(
                                headlineContent = { Text(tag) },
                                trailingContent = {
                                    IconButton(onClick = { onRemove(tag) }) {
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

@Composable
fun BulkModeManagerDialog(
    viewModel: GameListViewModel,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIds by viewModel.selectedGameIds.collectAsState()
    val allGames by viewModel.allGames.collectAsState()
    
    // Find common modes if possible, but usually for bulk we just want to set them all
    val options = listOf("Singleplayer", "Multiplayer", "Co-op")
    val selectedModes = remember { 
        mutableStateListOf<String>().apply { 
            // Default to modes from the first selected game if any
            val firstGame = allGames.find { it.id in selectedIds }
            firstGame?.gameModes?.let { addAll(it) }
        } 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Modes for Selection") },
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
            TextButton(onClick = { onConfirm(selectedModes.toList()); onDismiss() }) {
                Text("Apply to All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptInFlowRowForBulk(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = { content() }
    )
}

@Preview(showBackground = true)
@Composable
fun GameListScreenPreview() {
    PcGameCollectionTheme {
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
