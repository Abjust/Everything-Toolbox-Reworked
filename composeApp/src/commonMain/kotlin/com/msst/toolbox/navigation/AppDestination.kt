package com.msst.toolbox.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val label: String,
    val icon: ImageVector
) {
    Home("主页", Icons.Default.Home),
    Tools("工具", Icons.Default.Build),
    Settings("设置", Icons.Default.Settings),
    About("关于", Icons.Default.Info)
}