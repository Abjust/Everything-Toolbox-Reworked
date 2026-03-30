package com.msst.toolbox.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberLocationPermState(): LocationPermState = LocationPermState(true) {}