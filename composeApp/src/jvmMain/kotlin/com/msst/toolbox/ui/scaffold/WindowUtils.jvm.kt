package com.msst.toolbox.ui.scaffold

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
actual fun rememberIsLandscape(): Boolean {
    val containerSize = LocalWindowInfo.current.containerSize
    return containerSize.width >= containerSize.height
}