package com.msst.toolbox

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.msst.toolbox.navigation.AppDestination
import com.msst.toolbox.ui.about.AboutScreen
import com.msst.toolbox.ui.home.HomeScreen
import com.msst.toolbox.ui.scaffold.AppScaffold
import com.msst.toolbox.ui.settings.SettingsScreen
import com.msst.toolbox.ui.theme.AppTheme
import com.msst.toolbox.ui.tools.ToolsScreen

@Composable
fun App() {
    AppTheme {
        var currentDest by remember { mutableStateOf(AppDestination.Home) }

        AppScaffold(
            currentDest = currentDest,
            onDestChange = { currentDest = it }
        ) { paddingValues ->
            when (currentDest) {
                AppDestination.Home -> HomeScreen(paddingValues = paddingValues)
                AppDestination.Tools -> ToolsScreen(paddingValues = paddingValues)
                AppDestination.Settings -> SettingsScreen(paddingValues = paddingValues)
                AppDestination.About -> AboutScreen(paddingValues = paddingValues)
            }
        }
    }
}