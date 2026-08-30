package com.github.hagendietrich.pcgamecollection.ui.components

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PlaytimeComparisonGraph(
    userPlaytimeMinutes: Int,
    hltbMain: Int?,
    hltbMainExtra: Int?,
    hltbCompletionist: Int?,
    modifier: Modifier = Modifier,
    source: String? = "HLTB"
) {
    Log.d("PlaytimeGraph", "UI received HLTB: Main=$hltbMain, Extra=$hltbMainExtra, Comp=$hltbCompletionist, User=$userPlaytimeMinutes, Source=$source")
    if (hltbMain == null || hltbMain == 0) return

    val maxTime = (listOfNotNull(hltbMain, hltbMainExtra, hltbCompletionist, userPlaytimeMinutes).maxOrNull() ?: 1).toFloat()
    
    val mainColor = Color(0xFF90CAF9)
    val extraColor = Color(0xFF42A5F5)
    val compColor = Color(0xFF1E88E5)
    val userColor = MaterialTheme.colorScheme.primary

    val sourceDisplay = if (source == "IGDB") "IGDB.com" else "HowLongToBeat"

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(
            text = "Community Playtimes ($sourceDisplay)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                val barHeight = 12.dp.toPx()
                val yOffset = (height - barHeight) / 2

                // Draw background bar (gray)
                drawRoundRect(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    topLeft = Offset(0f, yOffset),
                    size = Size(width, barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )

                // Draw Completionist (Darkest)
                if (hltbCompletionist != null) {
                    drawRoundRect(
                        color = compColor,
                        topLeft = Offset(0f, yOffset),
                        size = Size(width * (hltbCompletionist / maxTime), barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }

                // Draw Main + Extra (Medium)
                if (hltbMainExtra != null) {
                    drawRoundRect(
                        color = extraColor,
                        topLeft = Offset(0f, yOffset),
                        size = Size(width * (hltbMainExtra / maxTime), barHeight),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }

                // Draw Main Story (Lightest)
                drawRoundRect(
                    color = mainColor,
                    topLeft = Offset(0f, yOffset),
                    size = Size(width * (hltbMain / maxTime), barHeight),
                    cornerRadius = CornerRadius(6.dp.toPx())
                )

                // Draw User Marker
                val userMarkerX = width * (userPlaytimeMinutes / maxTime)
                drawCircle(
                    color = userColor,
                    radius = 6.dp.toPx(),
                    center = Offset(userMarkerX, height / 2)
                )
                
                // White stroke for visibility
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = Offset(userMarkerX, height / 2)
                )
                
                drawCircle(
                    color = userColor,
                    radius = 3.dp.toPx(),
                    center = Offset(userMarkerX, height / 2)
                )
            }
        }

        // Legend/Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val mainLabel = if (source == "IGDB") "Completed" else "Main"
            val extraLabel = if (source == "IGDB") "Main+Extras" else "Extra"
            
            LegendItem(mainLabel, formatTime(hltbMain), mainColor)
            hltbMainExtra?.let { LegendItem(extraLabel, formatTime(it), extraColor) }
            hltbCompletionist?.let { LegendItem("100%", formatTime(it), compColor) }
        }
    }
}

@Composable
private fun LegendItem(label: String, time: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = time, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
