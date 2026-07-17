package com.streambert.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * FIXED LAYER — Scrim/gradient overlay.
 *
 * A SINGLE Box with continuous gradients. Static, anchored to the screen,
 * does NOT scroll. The backdrop remains full-bleed behind everything.
 *
 * Vertical: dark at top (behind hero info text) → gradually fading to a
 * lighter ambient level behind the rows. Smooth blend, no hard edge.
 *
 * Horizontal: dark left edge (nav rail legibility) → transparent.
 *
 * Both are applied as .background() modifiers on the same Box (paint layers
 * on one surface, not separate composables) to avoid any seam.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.80f),
                        0.20f to Color.Black.copy(alpha = 0.65f),
                        0.40f to Color.Black.copy(alpha = 0.40f),
                        0.60f to Color.Black.copy(alpha = 0.25f),
                        0.80f to Color.Black.copy(alpha = 0.15f),
                        1.00f to Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.60f),
                        0.12f to Color.Black.copy(alpha = 0.35f),
                        0.30f to Color.Black.copy(alpha = 0.10f),
                        0.50f to Color.Transparent
                    )
                )
            )
    )
}
