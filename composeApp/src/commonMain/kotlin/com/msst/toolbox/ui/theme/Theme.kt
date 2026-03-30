package com.msst.toolbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AppColorScheme = lightColorScheme(
    primary = LightBlue500,
    onPrimary = Color.White,
    primaryContainer = LightBlue100,
    onPrimaryContainer = LightBlue900,
    secondary = Blue500,
    onSecondary = Color.White,
    secondaryContainer = Blue100,
    onSecondaryContainer = Blue700,
    tertiary = LightBlue700,
    onTertiary = Color.White,
    background = SurfaceBlue,
    onBackground = OnSurfaceDark,
    surface = Color.White,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariant,
    surfaceVariant = LightBlue50,
    outline = LightBlue200,
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}