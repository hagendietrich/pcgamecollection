package com.example.digitalcollectionmanager.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.digitalcollectionmanager.R
import com.example.digitalcollectionmanager.ui.viewmodel.GameDetailUiState
import com.example.digitalcollectionmanager.ui.viewmodel.GameDetailViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    viewModel: GameDetailViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = (scrollState.value / 300f).coerceIn(0f, 0.9f))
                )
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
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        // Parallax Header (Screenshots)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .graphicsLayer {
                                    translationY = scrollState.value * 0.5f
                                    alpha = (1f - (scrollState.value / 600f)).coerceIn(0f, 1f)
                                }
                        ) {
                            if (game.screenshotUrls.isNotEmpty()) {
                                val pagerState = rememberPagerState(pageCount = { game.screenshotUrls.size })
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
                            } else {
                                AsyncImage(
                                    model = game.coverImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().clickable { 
                                        fullScreenImageUrl = game.coverImageUrl
                                    },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }

                        // Content Body
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                // Basic Info Row (Rating & Date)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (game.userRating != null) {
                                            Icon(Icons.Default.Star, contentDescription = "User Rating", tint = Color(0xFFFFD700), modifier = Modifier.size(18.dp))
                                            Text(
                                                text = stringResource(R.string.rating_users) + " ${game.userRating.roundToInt()}%",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                                            )
                                        }
                                        if (game.criticRating != null) {
                                            Icon(Icons.Default.Star, contentDescription = "Critic Rating", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            Text(
                                                text = stringResource(R.string.rating_critics) + " ${game.criticRating.roundToInt()}%",
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                    if (game.releaseDate != null) {
                                        Text(
                                            text = game.releaseDate,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.secondary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Companies
                                if (game.developers.isNotEmpty()) {
                                    Text(
                                        text = "Developed by: ${game.developers.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (game.publishers.isNotEmpty()) {
                                    Text(
                                        text = "Published by: ${game.publishers.joinToString(", ")}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Store Links
                                if (game.storeUrls.isNotEmpty() || !game.igdbUrl.isNullOrBlank()) {
                                    Text("Official Links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    OptInFlowRow(modifier = Modifier.padding(top = 8.dp)) {
                                        game.storeUrls.forEach { (name, url) ->
                                            AssistChip(
                                                onClick = { 
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) 
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                },
                                                label = { Text(name) },
                                                leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                            )
                                        }
                                        if (!game.igdbUrl.isNullOrBlank()) {
                                            AssistChip(
                                                onClick = { 
                                                    try {
                                                        context.startActivity(Intent(Intent.ACTION_VIEW, game.igdbUrl.toUri())) 
                                                    } catch (e: Exception) {
                                                        e.printStackTrace()
                                                    }
                                                },
                                                label = { Text("IGDB") },
                                                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(24.dp))
                                }

                                // Summary
                                if (!game.summary.isNullOrBlank()) {
                                    Text(
                                        text = "About",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = game.summary,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(top = 8.dp),
                                        textAlign = TextAlign.Justify
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Genres & Themes
                                if (game.genres.isNotEmpty() || game.themes.isNotEmpty()) {
                                    Text("Categories", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    OptInFlowRow(modifier = Modifier.padding(top = 8.dp)) {
                                        game.genres.forEach { genre ->
                                            SuggestionChip(onClick = {}, label = { Text(genre) })
                                        }
                                        game.themes.forEach { theme ->
                                            SuggestionChip(onClick = {}, label = { Text(theme) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(64.dp))
                            }
                        }
                    }
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
                dismissOnClickOutside = false // Prevent accidental closure while zooming
            )
        ) {
            var scale by remember { mutableStateOf(1f) }
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
                                // Only pan if zoomed in
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptInFlowRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() }
    )
}
