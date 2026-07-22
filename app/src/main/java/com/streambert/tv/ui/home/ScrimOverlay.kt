package com.streambert.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Apple TV-style scrim overlay.
 *
 * Near-black (#000000–#0A0A0A) is the focal background. The scrim is soft
 * and minimal — just enough to keep overlaid text legible without a hard box.
 * The gradient goes from solid near-black at the bottom (where rows live) to
 * mostly transparent at the top (where the hero artwork shines through).
 *
 * Apple TV's design: content is the focal point, minimal chrome.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            // Vertical: soft gradient from transparent at top to near-black at bottom
            // This lets the hero artwork breathe while keeping row labels legible
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to Color.Transparent,
                        0.25f to Color(0x1A000000),       // very subtle at hero area
                        0.45f to Color(0x66000000),       // transition zone
                        0.60f to Color(0xB3000000),       // getting dark for row readability
                        0.75f to Color(0xE6050505),       // near-black
                        1.00f to Color(0xFF0A0A0A)        // Apple TV bottom: #0A0A0A
                    )
                )
            )
            // Horizontal: gentle left-edge darkening for nav rail
            .background(
                Brush.horizontalGradient(
                    colorStops = arrayOf(
                        0.00f to Color(0x99000000),
                        0.08f to Color(0x4D000000),
                        0.20f to Color.Transparent,
                        1.00f to Color.Transparent
                    )
                )
            )
    )
}
