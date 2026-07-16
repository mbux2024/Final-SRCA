package com.streambert.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val StreambertColors = darkColorScheme(
    primary = NetflixRed,
    onPrimary = Color.White,
    secondary = PrimeSurface,
    onSecondary = TextPrimary,
    background = PrimeBg,
    onBackground = TextPrimary,
    surface = PrimeSurface,
    onSurface = TextPrimary,
    surfaceVariant = PrimeSurfaceHigh,
    onSurfaceVariant = TextSecondary,
    border = UnfocusedBorder
)

@Composable
fun StreambertTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreambertColors,
        content = content
    )
}
