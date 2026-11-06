package com.hubops.vislumbrops.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HubOpsColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    secondary = Color(0xFF00E5FF),
    background = Color(0xFF0E0E12),
    surface = Color(0xFF1A1A24),
    onBackground = Color(0xFFEDEDED),
    onSurface = Color(0xFFEDEDED)
)

@Composable
fun HubOpsTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = HubOpsColors, content = content)
