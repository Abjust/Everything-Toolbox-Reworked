package com.msst.toolbox

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val windowState = rememberWindowState(
        size = DpSize(1024.dp, 768.dp)
    )
    Window(
        onCloseRequest = ::exitApplication,
        title = BuildInfo.APP_NAME,
        state = windowState
    ) {
        App()
    }
}