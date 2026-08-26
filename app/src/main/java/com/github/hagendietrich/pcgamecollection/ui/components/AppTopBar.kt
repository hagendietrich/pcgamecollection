package com.github.hagendietrich.pcgamecollection.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.github.hagendietrich.pcgamecollection.R
import com.github.hagendietrich.pcgamecollection.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onNavigate: (String) -> Unit,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    placeholderText: String = "Search...",
    showSearchToggle: Boolean = true,
    onToggleSearch: (Boolean) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isSearchActive) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { 
                        Text(
                            text = placeholderText,
                            style = MaterialTheme.typography.bodyMedium
                        ) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            } else {
                Text(
                    text = title,
                    modifier = Modifier.clickable(enabled = showSearchToggle) { onToggleSearch(true) }
                )
            }
        },
        actions = {
            if (!isSearchActive && showSearchToggle) {
                IconButton(onClick = { onToggleSearch(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            actions()
            Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_my_games)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Library.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_wishlist)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Wishlist.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_add_game)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.AddGame.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_sync)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Sync.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_import)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.ImportExport.route)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_settings)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.Setup.route)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_about)) },
                        onClick = {
                            showMenu = false
                            onNavigate(Screen.About.route)
                        }
                    )
                }
            }
        }
    )
}
