package com.github.hagendietrich.pcgamecollection.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.api.models.IgdbGame
import com.github.hagendietrich.pcgamecollection.data.model.Game
import kotlin.math.roundToInt

@Composable
fun DeleteConfirmDialog(
    game: Game,
    onDelete: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var addToIgnoreList by remember { mutableStateOf(value = false) }
    val hasOnlinePlatform = game.platforms.any { it == "Steam" || it == "GOG" || it == "Epic" || it == "Ubisoft" || it == "Battle.net" }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Game") },
        text = {
            Column {
                Text("Are you sure you want to delete \"${game.title}\"?")
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
                onClick = { onDelete(addToIgnoreList) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ManageTagsDialog(
    title: String,
    currentTags: List<String>,
    allSuggestions: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
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
                    label = { Text("Add New") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = { 
                            if (text.isNotBlank()) {
                                onAdd(text)
                                text = ""
                            }
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add")
                        }
                    }
                )
                
                if (currentTags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Current:", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        currentTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onRemove(tag) },
                                label = { Text(tag) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }

                if (allSuggestions.isNotEmpty()) {
                    val remainingSuggestions = allSuggestions.filter { it !in currentTags }
                    if (remainingSuggestions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Suggestions:", style = MaterialTheme.typography.labelMedium)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp).verticalScroll(rememberScrollState()).padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            remainingSuggestions.forEach { suggestion ->
                                AssistChip(
                                    onClick = { onAdd(suggestion) },
                                    label = { Text(suggestion) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
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

@Composable
fun AlternativeCoversDialog(
    covers: List<String>,
    isFetching: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Alternative Cover") },
        text = {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 500.dp)) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (covers.isEmpty()) {
                    Text("No alternative covers found.", modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(covers) { coverUrl ->
                            Card(
                                modifier = Modifier
                                    .aspectRatio(0.75f)
                                    .clickable { onSelect(coverUrl) },
                                shape = MaterialTheme.shapes.extraSmall,
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
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

@Composable
fun PersonalRatingDialog(
    initialRating: Int?,
    onConfirm: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var rating by remember { mutableFloatStateOf(initialRating?.toFloat() ?: 0f) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Personal Rating") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${rating.roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Slider(
                    value = rating,
                    onValueChange = { rating = it },
                    valueRange = 0f..100f,
                    steps = 100
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rating.roundToInt()) }) {
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
    isSearching: Boolean,
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
                    IconButton(onClick = { onSearch(searchText) }, enabled = !isSearching) {
                        Icon(Icons.Default.Search, contentDescription = "Search")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isSearching) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (candidates.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No results. Try refining your search.", style = MaterialTheme.typography.bodySmall)
                    }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ManageableChip(
    text: String,
    onDelete: () -> Unit,
    color: Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .combinedClickable(
                onClick = {},
                onLongClick = { showDeleteConfirm = true }
            ),
        shape = MaterialTheme.shapes.small,
        color = color,
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
