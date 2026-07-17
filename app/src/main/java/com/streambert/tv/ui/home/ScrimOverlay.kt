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
 * A single continuous vertical gradient spanning the ENTIRE screen height
 * with a smooth, gradual transition from dark (hero text area) to the
 * steady dim level (row area). No hard edges or seams.
 *
 * Plus a horizontal gradient for left-edge (nav rail) legibility.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    // Single vertical gradient — ONE Box, full screen height, smooth transition.
    // The transition between hero-text darkness and row-area darkness spans
    // ~15-20% of the screen so it reads as a smooth fade, not a line.
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.85f),  // behind hero title
                        0.15f to Color.Black.copy(alpha = 0.80f),  // still in hero zone
                        0.30f to Color.Black.copy(alpha = 0.55f),  // behind description
                        0.42f to Color.Black.copy(alpha = 0.40f),  // transition zone (slow fade)
                        0.55f to Color.Black.copy(alpha = 0.35f),  // settles here for rows
                        1.00f to Color.Black.copy(alpha = 0.35f)   // holds steady to bottom
                    )
                )
            )
    )
    // Horizontal gradient for left-edge legibility (nav rail + hero text).
    // Separate axis — no vertical seam risk.
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.70f),  // left edge (nav rail)
                        0.10f to Color.Black.copy(alpha = 0.45f),  // behind hero text start
                        0.30f to Color.Black.copy(alpha = 0.15f),  // fading
                        0.50f to Color.Transparent                 // gone by mid-screen
                    )
                )
            )
    )
}
