package com.example.digitalcollectionmanager.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.example.digitalcollectionmanager.R
import com.example.digitalcollectionmanager.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    onNavigate: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text(title) },
        actions = {
            actions()
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
                    text = { Text(stringResource(R.string.menu_add_game)) },
                    onClick = {
                        showMenu = false
                        onNavigate(Screen.AddGame.route)
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
            }
        }
    )
}
