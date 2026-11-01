package com.stuartjohnson.rainsong.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RainDark = darkColorScheme(
    primary = Color(0xFF7FDBD4),
    onPrimary = Color(0xFF003733),
    secondary = Color(0xFFB3C8C4),
    background = Color(0xFF071318),
    surface = Color(0xFF0E1F27),
    onBackground = Color(0xFFE3EEF0),
    onSurface = Color(0xFFE3EEF0),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RainSongTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RainDark,
        content = content,
    )
}
