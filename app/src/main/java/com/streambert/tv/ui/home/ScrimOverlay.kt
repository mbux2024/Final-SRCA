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
 * The hero/scrim zone extends down far enough to cover the hero text AND
 * the first content row beneath it, dimming that row significantly. The
 * gradient then gradually (no hard edge, no seam) fades to the lighter
 * ambient level by the time "Top 10 Movies Today" begins.
 *
 * Single continuous Brush.verticalGradient — many color stops for a
 * smooth, imperceptible transition. No two-Box seam, no abrupt jump.
 *
 * Plus horizontal gradient for left-edge (nav rail) legibility.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    // Single continuous vertical gradient spanning full screen height.
    // Gradual darkening covers the first row, then slowly lightens over
    // ~20% of screen height to settle at the ambient row level.
    // NO sharp edges anywhere.
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.88f),  // hero title area
                        0.12f to Color.Black.copy(alpha = 0.85f),  // hero metadata
                        0.25f to Color.Black.copy(alpha = 0.82f),  // hero description
                        0.38f to Color.Black.copy(alpha = 0.78f),  // below description
                        0.48f to Color.Black.copy(alpha = 0.72f),  // first row - heavily dimmed
                        0.55f to Color.Black.copy(alpha = 0.65f),  // first row - still dimmed
                        0.62f to Color.Black.copy(alpha = 0.55f),  // gradual fade continuing
                        0.68f to Color.Black.copy(alpha = 0.45f),  // approaching row area
                        0.74f to Color.Black.copy(alpha = 0.38f),  // nearly at ambient level
                        0.80f to Color.Black.copy(alpha = 0.35f),  // settled at row ambient
                        1.00f to Color.Black.copy(alpha = 0.35f)   // holds steady to bottom
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
