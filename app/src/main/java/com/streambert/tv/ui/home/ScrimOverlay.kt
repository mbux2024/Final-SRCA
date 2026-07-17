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
 * The hero/scrim zone extends down to cover the hero text AND the first
 * content row beneath it (genre chips + first poster row). A deliberate
 * sharp edge lands at ~65% of screen height — everything above is the
 * dark hero zone, everything below ("Top 10 Movies Today" onward) is the
 * normal ambient-dim row area.
 *
 * Vertical gradient:
 *   0-50%: dark/opaque hero zone (~80-85% black)
 *   50-62%: still covering first row beneath hero (~70-75% black)
 *   62-66%: SHARP drop (deliberate hard edge, ~4% span)
 *   66-100%: normal row area (~35% black, holds steady)
 *
 * Plus horizontal gradient for left-edge (nav rail) legibility.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    // Vertical gradient — dark hero zone extended to cover first row,
    // sharp edge before the second visible row.
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.88f),  // top: hero title
                        0.20f to Color.Black.copy(alpha = 0.85f),  // hero metadata
                        0.40f to Color.Black.copy(alpha = 0.80f),  // hero description
                        0.52f to Color.Black.copy(alpha = 0.75f),  // covering first row
                        0.60f to Color.Black.copy(alpha = 0.70f),  // still covering first row
                        0.63f to Color.Black.copy(alpha = 0.60f),  // sharp edge begins
                        0.66f to Color.Black.copy(alpha = 0.35f),  // sharp edge ends (hard cut)
                        0.70f to Color.Black.copy(alpha = 0.35f),  // row area (steady)
                        1.00f to Color.Black.copy(alpha = 0.35f)   // holds to bottom
                    )
                )
            )
    )

    // Horizontal gradient for left-edge legibility (nav rail + hero text).
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.70f),
                        0.10f to Color.Black.copy(alpha = 0.45f),
                        0.30f to Color.Black.copy(alpha = 0.15f),
                        0.50f to Color.Transparent
                    )
                )
            )
    )
}
