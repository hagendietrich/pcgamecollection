package com.github.hagendietrich.pcgamecollection.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.github.hagendietrich.pcgamecollection.data.model.Achievement

@Composable
fun AchievementSection(
    achievements: List<Achievement>,
    source: String?,
    modifier: Modifier = Modifier
) {
    if (achievements.isEmpty()) return

    val unlockedCount = achievements.count { it.isUnlocked }
    val totalCount = achievements.size
    
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var selectedAchievement by remember { mutableStateOf<Achievement?>(null) }

    val sortedAchievements = remember(achievements) {
        achievements.sortedByDescending { it.isUnlocked }
    }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Achievements (${source ?: "Unknown"}): ",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "$unlockedCount / $totalCount",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Show Less" else "Show All",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sortedAchievements.forEach { achievement ->
                    AchievementListItem(
                        achievement = achievement,
                        onClick = { selectedAchievement = achievement }
                    )
                }
            }
        }
    }

    if (selectedAchievement != null) {
        AchievementDetailDialog(
            achievement = selectedAchievement!!,
            onDismiss = { selectedAchievement = null }
        )
    }
}

@Composable
fun AchievementListItem(
    achievement: Achievement,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AchievementIcon(
            achievement = achievement,
            onClick = onClick,
            size = 48.dp
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column {
            Text(
                text = if (achievement.isHidden && !achievement.isUnlocked) "Hidden Achievement" else achievement.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (achievement.isUnlocked) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!(achievement.isHidden && !achievement.isUnlocked) && !achievement.description.isNullOrBlank()) {
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AchievementIcon(
    achievement: Achievement,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 56.dp
) {
    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = 2.dp,
                color = if (achievement.isUnlocked) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = achievement.iconUrl,
            contentDescription = achievement.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            colorFilter = if (!achievement.isUnlocked) ColorFilter.colorMatrix(grayscaleMatrix) else null,
            alpha = if (!achievement.isUnlocked) 0.6f else 1f
        )
        
        if (achievement.isHidden && !achievement.isUnlocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        }
    }
}

@Composable
fun AchievementDetailDialog(
    achievement: Achievement,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        title = {
            Text(
                text = if (achievement.isHidden && !achievement.isUnlocked) "Hidden Achievement" else achievement.name,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AchievementIcon(achievement = achievement, onClick = {})
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (achievement.isHidden && !achievement.isUnlocked) 
                        "This is a secret achievement. Unlock it to see the description." 
                        else achievement.description ?: "No description available.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                if (achievement.isUnlocked && achievement.unlockTime != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    // Format timestamp if possible
                    Text(
                        text = "Unlocked!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}
