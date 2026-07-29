package com.example.digitalcollectionmanager.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.digitalcollectionmanager.data.api.models.IgdbGame
import com.example.digitalcollectionmanager.data.repository.UnmatchedGame

@Composable
fun UnmatchedGameRow(
    unmatched: UnmatchedGame,
    onResolve: (IgdbGame) -> Unit,
    onSearchCustom: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var searchText by remember(unmatched.storeTitle) { mutableStateOf(unmatched.storeTitle) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(unmatched.storeTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("${unmatched.platform} ID: ${unmatched.storeId}", style = MaterialTheme.typography.labelSmall)
                }
                Button(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(if (expanded) "Close" else "Resolve", style = MaterialTheme.typography.labelMedium)
                }
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        label = { Text("Search on IGDB") },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        singleLine = true
                    )
                    Button(onClick = { onSearchCustom(searchText) }) {
                        Text("Search")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select the correct match:", style = MaterialTheme.typography.labelMedium)
                
                if (unmatched.candidates.isEmpty()) {
                    Text("No candidates found. Try refining the search above.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    unmatched.candidates.forEach { candidate ->
                        val year = candidate.firstReleaseDate?.let { 
                            java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault()).year 
                        }
                        
                        ListItem(
                            headlineContent = { Text(candidate.name) },
                            supportingContent = { Text(year?.toString() ?: "Unknown Year") },
                            modifier = Modifier.clickable { 
                                onResolve(candidate) 
                                expanded = false
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                    }
                }
            }
        }
    }
}
