package com.msst.toolbox.platform

import androidx.compose.runtime.Composable

data class LocationPermState(val isGranted: Boolean, val requestPermission: () -> Unit)

@Composable
expect fun rememberLocationPermState(): LocationPermState