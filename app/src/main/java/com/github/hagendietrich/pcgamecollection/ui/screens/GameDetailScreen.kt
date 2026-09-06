package com.github.hagendietrich.pcgamecollection.ui.screens

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import com.github.hagendietrich.pcgamecollection.data.model.Game
import com.github.hagendietrich.pcgamecollection.ui.components.*
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.GameDetailUiState
import com.github.hagendietrich.pcgamecollection.data.model.getCategoryDisplay
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.GameDetailViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    viewModel: GameDetailViewModel,
    onBack: () -> Unit,
    onNavigateToGame: (Int) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    
    // Dialog states
    var showPlaytimePicker by remember { mutableStateOf(value = false) }
    var showManageLabelsDialog by remember { mutableStateOf(false) }
    var showManageGenresDialog by remember { mutableStateOf(false) }
    var showManageSeriesDialog by remember { mutableStateOf(false) }
    var showManageFranchisesDialog by remember { mutableStateOf(false) }
    var showManagePlatformsDialog by remember { mutableStateOf(false) }
    var showReleaseDatePicker by remember { mutableStateOf(false) }
    var showEditModeDialog by remember { mutableStateOf(false) }
    var showAlternativeCoversDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showReMatchDialog by remember { mutableStateOf(false) }
    var showRatingPicker by remember { mutableStateOf(false) }

    var isEditingNotes by remember { mutableStateOf(false) }
    var editedNotes by remember { mutableStateOf("") }
    var showNotesSaveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    if (uiState is GameDetailUiState.Success) {
                        Text((uiState as GameDetailUiState.Success).game.title)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is GameDetailUiState.Success) {
                        val isSearching by viewModel.isSearchingReMatch.collectAsState()
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { 
                                val game = (uiState as GameDetailUiState.Success).game
                                viewModel.searchForReMatch(game.title)
                                showReMatchDialog = true 
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Re-match IGDB Metadata")
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            is GameDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is GameDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is GameDetailUiState.Success -> {
                val game = state.game
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(scrollState)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    // 1. Cover
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Box(modifier = Modifier.wrapContentSize()) {
                            AsyncImage(
                                model = game.coverImageUrl,
                                contentDescription = game.title,
                                modifier = Modifier
                                    .height(320.dp)
                                    .aspectRatio(0.75f)
                                    .clickable { fullScreenImageUrl = game.coverImageUrl },
                                contentScale = ContentScale.Crop
                            )
                            
                            val isFetchingCovers by viewModel.isFetchingCovers.collectAsState()
                            if (isFetchingCovers) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp).align(Alignment.TopEnd).padding(8.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                IconButton(
                                    onClick = { 
                                        viewModel.fetchAlternativeCovers()
                                        showAlternativeCoversDialog = true 
                                    },
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit, 
                                            contentDescription = "Change Cover",
                                            modifier = Modifier.padding(8.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Manual Refresh Button on the far right, aligned with the top of the cover
                        IconButton(
                            onClick = { viewModel.refreshMetadata() },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 0.dp) // Move as much as possible to the right
                                .size(48.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Default.CloudDownload, 
                                    contentDescription = "Refresh Metadata",
                                    modifier = Modifier.padding(8.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 2. Released
                    InfoRow(label = "Released", value = game.releaseDate ?: "Add Release Date", isClickable = true, onClick = { showReleaseDatePicker = true })
                    
                    // 3. Status
                    StatusRow(status = game.completionStatus, onStatusChange = { viewModel.updateGame(game.copy(completionStatus = it)) })
                    
                    // 4. Playtime
                    val hours = game.playtimeMinutes / 60
                    val minutes = game.playtimeMinutes % 60
                    InfoRow(label = "Playtime", value = "${hours}h ${minutes}m", isClickable = true, onClick = { showPlaytimePicker = true })
                    
                    if (game.hltbMain != null) {
                        PlaytimeComparisonGraph(
                            userPlaytimeMinutes = game.playtimeMinutes,
                            hltbMain = game.hltbMain,
                            hltbMainExtra = game.hltbMainExtra,
                            hltbCompletionist = game.hltbCompletionist,
                            source = game.playtimeSource
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 5. Platforms
                    ManageableDetailRow(
                        label = "Platforms",
                        items = game.platforms,
                        onLongClick = { showManagePlatformsDialog = true },
                        chipColor = { platform ->
                            val isOnline = platform == "Steam" || platform == "GOG" || platform == "Epic" || platform == "Ubisoft" || platform == "Battle.net"
                            if (isOnline) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        }
                    )
                    
                    // Achievement Section
                    AchievementSection(
                        achievements = game.achievements,
                        source = game.achievementsSource,
                        onToggleAchievement = { name, isUnlocked ->
                            viewModel.toggleAchievement(name, isUnlocked)
                        },
                        onFetchFromTrueAchievements = {
                            viewModel.fetchTrueAchievements()
                        }
                    )
                    
                    // 6. Labels
                    ManageableDetailRow(
                        label = "Labels",
                        items = game.labels,
                        onLongClick = { showManageLabelsDialog = true }
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 7. Ratings
                    if (game.userRating != null || game.criticRating != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            if (game.userRating != null) {
                                RatingItem(label = "Users", rating = game.userRating)
                            }
                            if (game.criticRating != null) {
                                RatingItem(label = "Critics", rating = game.criticRating)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Your rating Row
                    InfoRow(
                        label = "Your rating",
                        value = "${game.personalRating ?: 0}%",
                        isClickable = true,
                        onClick = { showRatingPicker = true }
                    )
                    
                    // 8. Developed by
                    if (game.developers.isNotEmpty()) {
                        InfoRow(label = "Developed by", value = game.developers.joinToString(", "))
                    }
                    
                     // 9. Published by
                     if (game.publishers.isNotEmpty()) {
                         InfoRow(label = "Published by", value = game.publishers.joinToString(", "))
                     }
 
                     // 10. Type
                     InfoRow(label = "Type", value = game.getCategoryDisplay(), isClickable = false)

                    
                    // DLCs / Main Game Link
                    val isBundle = game.category == 3 || game.category == 13
                    if (state.parentGame != null && !isBundle) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Main Game: ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            DetailChip(
                                text = state.parentGame.title,
                                onClick = { onNavigateToGame(state.parentGame.id) }
                            )
                        }
                    } else if (game.parentIgdbId != null && !isBundle) {
                        // Game has a parent but it's not in the library
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Main Game: ",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Not in Library",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (state.dlcs.isNotEmpty() || state.wishlistDlcs.isNotEmpty()) {
                        RelatedRow(
                            relatedGames = state.dlcs,
                            wishlistDlcs = state.wishlistDlcs,
                            onNavigateToGame = onNavigateToGame
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 10. Modus
                    ManageableDetailRow(
                        label = "Modus",
                        items = game.gameModes,
                        onLongClick = { showEditModeDialog = true }
                    )
                    
                    // 11. Genres
                    ManageableDetailRow(
                        label = "Genres",
                        items = game.genres,
                        onLongClick = { showManageGenresDialog = true }
                    )
                    
                    // 11a. Series
                    ManageableDetailRow(
                        label = "Series",
                        items = game.series,
                        onLongClick = { showManageSeriesDialog = true }
                    )
                    
                    // 11b. Franchises
                    ManageableDetailRow(
                        label = "Franchises",
                        items = game.franchises,
                        onLongClick = { showManageFranchisesDialog = true }
                    )
                    
                    // 12. Categories (Themes/Keywords, excluding genres)
                    val categories = (game.themes + game.keywords).distinct()
                    ManageableDetailRow(
                        label = "Categories",
                        items = categories,
                        onLongClick = {} // Read-only enrichment usually
                    )
                    
                    // 13. Official Links
                    LinksDetailRow(
                        label = "Official Links",
                        igdbUrl = game.igdbUrl,
                        storeUrls = game.storeUrls,
                        context = context
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // 14. Screenshots
                    if (game.screenshotUrls.isNotEmpty()) {
                        Text("Screenshots:", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        val pagerState = rememberPagerState(pageCount = { game.screenshotUrls.size })
                        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                AsyncImage(
                                    model = game.screenshotUrls[page],
                                    contentDescription = "Screenshot ${page + 1}",
                                    modifier = Modifier.fillMaxSize().clickable { 
                                        fullScreenImageUrl = game.screenshotUrls[page]
                                    },
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // Pager Indicators
                            if (game.screenshotUrls.size > 1) {
                                Row(
                                    Modifier
                                        .height(24.dp)
                                        .fillMaxWidth()
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    repeat(game.screenshotUrls.size) { iteration ->
                                        val color = if (pagerState.currentPage == iteration) Color.White else Color.White.copy(alpha = 0.5f)
                                        Box(
                                            modifier = Modifier
                                                .padding(2.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                                .size(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    
                    // 15. About
                    if (!game.summary.isNullOrBlank()) {
                        Text(
                            text = "About:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = game.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Justify
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                    
                    // 15a. Notes
                    Text(
                        text = "Notes:",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isEditingNotes) {
                        BackHandler {
                            if (editedNotes != (game.notes ?: "")) {
                                showNotesSaveDialog = true
                            } else {
                                isEditingNotes = false
                            }
                        }
                        OutlinedTextField(
                            value = editedNotes,
                            onValueChange = { editedNotes = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .onFocusChanged { 
                                    if (!it.isFocused && isEditingNotes && editedNotes != (game.notes ?: "")) {
                                        showNotesSaveDialog = true
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyLarge,
                            placeholder = { Text("Add your personal notes here...") }
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { 
                                isEditingNotes = false 
                                editedNotes = game.notes ?: ""
                            }) {
                                Text("Cancel")
                            }
                            Button(onClick = { 
                                isEditingNotes = false
                                viewModel.updateGame(game.copy(notes = editedNotes))
                            }) {
                                Text("Save")
                            }
                        }
                    } else {
                        Text(
                            text = if (game.notes.isNullOrBlank()) "Tap to add notes..." else game.notes,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .clickable { 
                                    editedNotes = game.notes ?: ""
                                    isEditingNotes = true 
                                },
                            color = if (game.notes.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(32.dp))

                    // 16. Date Added
                    val dateAddedStr = remember(game.dateAdded) {
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(game.dateAdded))
                    }
                    InfoRow(label = "Date Added", value = dateAddedStr)
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // 17. Delete Button
                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.detail_remove))
                    }
                    
                    Spacer(modifier = Modifier.height(64.dp))
                }
                
                // Dialogs
                if (showDeleteConfirmDialog) {
                    DeleteConfirmDialog(
                        game = game,
                        onDelete = { andIgnore: Boolean ->
                            viewModel.deleteGame(game, andIgnore)
                            onBack()
                        },
                        onDismiss = { showDeleteConfirmDialog = false }
                    )
                }

                if (showPlaytimePicker) {
                    PlaytimePickerDialog(
                        initialMinutes = game.playtimes["Manual"] ?: 0,
                        onDismiss = { showPlaytimePicker = false },
                        onConfirm = { newMinutes: Int ->
                            val updatedPlaytimes = game.playtimes.toMutableMap()
                            updatedPlaytimes["Manual"] = newMinutes
                            val totalPlaytime = updatedPlaytimes.values.sum()
                            viewModel.updateGame(game.copy(
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
                        onConfirm = { newDate: String ->
                            viewModel.updateReleaseDate(newDate)
                            showReleaseDatePicker = false 
                        },
                        onDismiss = { showReleaseDatePicker = false }
                    )
                }

                if (showManageLabelsDialog) {
                    val allLabels by viewModel.allLabels.collectAsState()
                    ManageTagsDialog(
                        title = "Manage Labels",
                        currentTags = game.labels,
                        allSuggestions = allLabels,
                        onAdd = { viewModel.addLabelToGame(it) },
                        onRemove = { viewModel.removeLabelFromGame(it) },
                        onDismiss = { showManageLabelsDialog = false }
                    )
                }

                if (showManageGenresDialog) {
                    val allGenres by viewModel.allGenres.collectAsState()
                    ManageTagsDialog(
                        title = "Manage Genres",
                        currentTags = game.genres,
                        allSuggestions = allGenres,
                        onAdd = { viewModel.addGenreToGame(it) },
                        onRemove = { viewModel.removeGenreFromGame(it) },
                        onDismiss = { showManageGenresDialog = false }
                    )
                }

                if (showManageSeriesDialog) {
                    val allSeries by viewModel.allSeries.collectAsState()
                    ManageTagsDialog(
                        title = "Manage Series",
                        currentTags = game.series,
                        allSuggestions = allSeries,
                        onAdd = { viewModel.addSeriesToGame(it) },
                        onRemove = { viewModel.removeSeriesFromGame(it) },
                        onDismiss = { showManageSeriesDialog = false }
                    )
                }

                if (showManageFranchisesDialog) {
                    val allFranchises by viewModel.allFranchises.collectAsState()
                    ManageTagsDialog(
                        title = "Manage Franchises",
                        currentTags = game.franchises,
                        allSuggestions = allFranchises,
                        onAdd = { viewModel.addFranchiseToGame(it) },
                        onRemove = { viewModel.removeFranchiseFromGame(it) },
                        onDismiss = { showManageFranchisesDialog = false }
                    )
                }

                if (showManagePlatformsDialog) {
                    val allPlatforms by viewModel.allPlatforms.collectAsState()
                    ManageTagsDialog(
                        title = "Manage Platforms",
                        currentTags = game.platforms,
                        allSuggestions = allPlatforms,
                        onAdd = { viewModel.addPlatformToGame(it) },
                        onRemove = { viewModel.removePlatformFromGame(it) },
                        onDismiss = { showManagePlatformsDialog = false }
                    )
                }

                if (showEditModeDialog) {
                    EditModeDialog(
                        currentModes = game.gameModes,
                        onConfirm = { modes: List<String> ->
                            viewModel.updateGameModes(modes)
                            showEditModeDialog = false 
                        },
                        onDismiss = { showEditModeDialog = false }
                    )
                }

                if (showAlternativeCoversDialog) {
                    val alternativeCovers by viewModel.alternativeCovers.collectAsState()
                    val isFetching by viewModel.isFetchingCovers.collectAsState()

                    AlternativeCoversDialog(
                        covers = alternativeCovers,
                        isFetching = isFetching,
                        onSelect = { url: String ->
                            viewModel.updateGameCover(url)
                            showAlternativeCoversDialog = false 
                        },
                        onDismiss = { 
                            viewModel.clearAlternativeCovers()
                            showAlternativeCoversDialog = false 
                        }
                    )
                }
                
                if (showReMatchDialog) {
                    val reMatchResults by viewModel.reMatchResults.collectAsState()
                    val isSearching by viewModel.isSearchingReMatch.collectAsState()
                    ReMatchDialog(
                        initialTitle = game.title,
                        candidates = reMatchResults,
                        isSearching = isSearching,
                        onSearch = { query: String -> viewModel.searchForReMatch(query) },
                        onSelect = { candidate: IgdbGame ->
                            viewModel.applyReMatch(candidate)
                            showReMatchDialog = false
                        },
                        onDismiss = {
                            viewModel.clearReMatchResults()
                            showReMatchDialog = false
                        }
                    )
                }

                if (showRatingPicker) {
                    PersonalRatingDialog(
                        initialRating = game.personalRating,
                        onConfirm = { 
                            viewModel.updatePersonalRating(it)
                            showRatingPicker = false 
                        },
                        onDismiss = { showRatingPicker = false }
                    )
                }

                if (showNotesSaveDialog) {
                    AlertDialog(
                        onDismissRequest = { showNotesSaveDialog = false },
                        title = { Text("Unsaved Notes") },
                        text = { Text("You have unsaved changes in your notes. Would you like to save them?") },
                        confirmButton = {
                            Button(onClick = {
                                viewModel.updateGame(game.copy(notes = editedNotes))
                                isEditingNotes = false
                                showNotesSaveDialog = false
                            }) {
                                Text("Save")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = {
                                isEditingNotes = false
                                showNotesSaveDialog = false
                            }) {
                                Text("Discard")
                            }
                        }
                    )
                }
            }
        }
    }
    
    // Full Screen Image Viewer
    if (fullScreenImageUrl != null) {
        Dialog(
            onDismissRequest = { fullScreenImageUrl = null },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false
            )
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = fullScreenImageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { fullScreenImageUrl = null },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, isClickable: Boolean = false, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label: ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (isClickable && (value.contains("Add") || value.isBlank())) androidx.compose.ui.text.style.TextDecoration.Underline else null
            ),
            modifier = Modifier.clickable(enabled = isClickable) { onClick() }
        )
    }
}

@Composable
fun StatusRow(status: CompletionStatus, onStatusChange: (CompletionStatus) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "Status: ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        var expanded by remember { mutableStateOf(false) }
        Box {
            TextButton(onClick = { expanded = true }) {
                Text(status.name, style = MaterialTheme.typography.bodyLarge)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CompletionStatus.entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry.name) },
                        onClick = {
                            onStatusChange(entry)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RatingItem(label: String, rating: Double) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(20.dp))
            Text(
                text = "${rating.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageableDetailRow(
    label: String,
    items: List<String>,
    onLongClick: () -> Unit,
    chipColor: @Composable (String) -> Color = { MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f) }
) {
    var expanded by remember { mutableStateOf(false) }
    val canExpand = items.isNotEmpty()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { 
                    if (canExpand) expanded = !expanded 
                    else onLongClick()
                },
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (items.isEmpty()) {
                Text(
                    text = "Add...",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                    )
                )
            } else if (!expanded) {
                OptInFlowRow(
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items.forEach { item ->
                        DetailChip(text = item, color = chipColor(item))
                    }
                }
            }
        }
        
        if (expanded && items.isNotEmpty()) {
            OptInFlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items.forEach { item ->
                    DetailChip(text = item, color = chipColor(item))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LinksDetailRow(
    label: String,
    igdbUrl: String?,
    storeUrls: Map<String, String>,
    context: android.content.Context
) {
    var expanded by remember { mutableStateOf(false) }
    val hasLinks = !igdbUrl.isNullOrBlank() || storeUrls.isNotEmpty()
    
    val links = mutableListOf<Pair<String, String>>()
    storeUrls.forEach { (name, url) -> links.add(name to url) }
    if (!igdbUrl.isNullOrBlank()) links.add("IGDB" to igdbUrl)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasLinks) { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            if (!hasLinks) {
                Text(
                    text = "None",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            } else if (!expanded) {
                OptInFlowRow(
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    links.forEach { (name, url) ->
                        DetailChip(
                            text = name,
                            leadingIcon = {
                                Icon(
                                    if (name == "IGDB") Icons.Default.Language else Icons.Default.Store,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }
                }
            }
        }
        
        if (expanded && hasLinks) {
            OptInFlowRow(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                links.forEach { (name, url) ->
                    DetailChip(
                        text = name,
                        leadingIcon = {
                            Icon(
                                if (name == "IGDB") Icons.Default.Language else Icons.Default.Store,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RelatedRow(
    relatedGames: List<Game>,
    wishlistDlcs: List<com.github.hagendietrich.pcgamecollection.data.model.WishlistGame>,
    onNavigateToGame: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Related: ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${relatedGames.size + wishlistDlcs.size} items",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand"
            )
        }
        
        if (expanded) {
            Column(
                modifier = Modifier.padding(top = 8.dp, start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                relatedGames.forEach { game ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToGame(game.id) }
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (game.isOwned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (game.isOwned) "Owned" else "Not Owned",
                            tint = if (game.isOwned) Color(0xFF4CAF50) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                wishlistDlcs.forEach { dlc ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Wishlist",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = dlc.title,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptInFlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    maxLines: Int = Int.MAX_VALUE,
    content: @Composable () -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        maxLines = maxLines,
        content = { content() }
    )
}

@Composable
fun DetailChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        onClick = onClick ?: {},
        enabled = onClick != null,
        shape = MaterialTheme.shapes.extraSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        color = color,
        modifier = modifier.padding(vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}
