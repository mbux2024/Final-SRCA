package com.streambert.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Streambert brand palette (mirrors the desktop app's red accent on dark bg)
val Background = Color(0xFF0B0B0F)
val Surface = Color(0xFF15151C)
val SurfaceVariant = Color(0xFF20202B)
val Red = Color(0xFFE50914)
val TextPrimary = Color(0xFFF5F5F7)
val TextSecondary = Color(0xFFB5B5BE)
val TextTertiary = Color(0xFF7A7A85)

private val StreambertColors = darkColorScheme(
    primary = Red,
    onPrimary = Color.White,
    secondary = SurfaceVariant,
    onSecondary = TextPrimary,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = TextSecondary,
    border = SurfaceVariant
)

@Composable
fun StreambertTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreambertColors,
        content = content
    )
}
