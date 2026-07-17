package com.streambert.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * FIXED LAYER — Scrim/gradient overlays.
 *
 * Static, anchored to the screen, does NOT scroll.
 *
 * Horizontal: dark left (behind nav rail + hero text) → transparent right.
 * Vertical: ~80-90% black at top (hero text) → ~30-40% in row area, HOLDS
 * STEADY at that level (does not fade to transparent). Backdrop bleeds
 * through as ambient mood; focused card = brightest thing on screen.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Horizontal: dark left → transparent right
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xE6000000),
                            0.12f to Color(0xCC000000),
                            0.30f to Color(0x66000000),
                            0.50f to Color.Transparent
                        )
                    )
                )
        )
        // Vertical: dark top → steady ~35% in row area
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xE0000000),
                            0.15f to Color(0xCC000000),
                            0.35f to Color(0x66000000),
                            0.50f to Color(0x59000000),
                            1.00f to Color(0x59000000)
                        )
                    )
                )
        )
    }
}
