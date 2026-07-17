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
 * A SINGLE Box with ONE continuous Brush.verticalGradient spanning the full
 * screen height. No stacked layers, no seam, no hard edge.
 *
 * The gradient covers the hero text and dims the first row, then gradually
 * eases to the ambient row level. The horizontal darkening for the nav rail
 * is applied to the SAME Box via a second background modifier (stacking
 * backgrounds on one Box does NOT create a seam — they render as paint layers
 * on the same surface, not separate bounded composables).
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.85f),
                        0.25f to Color.Black.copy(alpha = 0.75f),
                        0.50f to Color.Black.copy(alpha = 0.55f),
                        0.75f to Color.Black.copy(alpha = 0.40f),
                        1.00f to Color.Black.copy(alpha = 0.35f)
                    )
                )
            )
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Black.copy(alpha = 0.50f),
                        0.12f to Color.Black.copy(alpha = 0.25f),
                        0.30f to Color.Transparent,
                        1.00f to Color.Transparent
                    )
                )
            )
    )
}
