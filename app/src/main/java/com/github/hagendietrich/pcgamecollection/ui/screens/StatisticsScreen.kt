package com.github.hagendietrich.pcgamecollection.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.data.model.CompletionStatus
import com.github.hagendietrich.pcgamecollection.ui.components.AppTopBar
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.StatisticsUiState
import com.github.hagendietrich.pcgamecollection.ui.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.statistics_title),
                onNavigate = {}, // Not needed as we use onBack
                showSearchToggle = false,
                actions = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SummarySection(uiState)
            }

            item {
                StatusDistribution(uiState.gamesByStatus)
            }

            item {
                PlatformDistribution(uiState.gamesByPlatform)
            }

            if (uiState.topGamesByPlaytime.isNotEmpty()) {
                item {
                    Text(
                        text = "Top Games by Playtime",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.topGamesByPlaytime) { game ->
                    val hours = game.playtimeMinutes / 60
                    val minutes = game.playtimeMinutes % 60
                    ListItem(
                        headlineContent = { Text(game.title) },
                        trailingContent = { Text("${hours}h ${minutes}m") },
                        leadingContent = { Icon(Icons.Default.Gamepad, contentDescription = null) }
                    )
                }
            }

            if (uiState.genreDistribution.isNotEmpty()) {
                item {
                    Text(
                        text = "Genre Distribution",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(uiState.genreDistribution.toList()) { (genre, count) ->
                    ListItem(
                        headlineContent = { Text(genre) },
                        trailingContent = { Text(count.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
fun SummarySection(state: StatisticsUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatCard(
            label = "Total Games",
            value = state.totalOwnedGames.toString(),
            icon = Icons.Default.Gamepad,
            modifier = Modifier.weight(1f)
        )
        val hours = state.totalPlaytimeMinutes / 60
        StatCard(
            label = "Total Playtime",
            value = "${hours}h",
            icon = Icons.Default.Timer,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(text = label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun StatusDistribution(statusMap: Map<CompletionStatus, Int>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Library Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            statusMap.forEach { (status, count) ->
                val displayName = when(status) {
                    CompletionStatus.COMPLETED -> "Completed"
                    CompletionStatus.PLAYING -> "Playing"
                    CompletionStatus.ON_HOLD -> "On_Hold"
                    CompletionStatus.BACKLOG -> "Backlog"
                    CompletionStatus.ABANDONED -> "Abandoned"
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(displayName)
                    Text(count.toString(), fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { count.toFloat() / statusMap.values.sum().coerceAtLeast(1).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun PlatformDistribution(platformMap: Map<String, Int>) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Platforms",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            platformMap.forEach { (platform, count) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(platform)
                    Text(count.toString(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
