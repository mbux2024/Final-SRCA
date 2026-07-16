package com.streambert.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * LAYER 2 — Scrim/gradient overlays (z-index 1).
 *
 * Two gradient overlays anchored to the screen (do NOT scroll with content):
 *
 * 1. Horizontal gradient: solid dark on the left (behind nav rail + hero text)
 *    fading to transparent toward the right.
 *
 * 2. Vertical gradient: concentrated as a solid dark scrim ONLY behind the hero
 *    text block near the top (~top 40%), fading to FULLY TRANSPARENT by the time
 *    it reaches the content rows below. The backdrop image remains visible/bleeding
 *    through behind the rows for the entire scroll length.
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        // Horizontal: dark left edge (nav rail + hero text legibility) → transparent right
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xE6000000),   // nearly opaque at left edge
                            0.15f to Color(0xB3000000),  // still strong behind text
                            0.35f to Color(0x4D000000),  // fading
                            0.55f to Color.Transparent   // fully transparent by mid-screen
                        )
                    )
                )
        )

        // Vertical: solid dark scrim at the top (hero info area) fading to
        // fully transparent before the content rows begin (~40% down).
        // The backdrop bleeds through unobstructed behind the scrolling rows.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xCC000000),   // strong at very top (nav bar)
                            0.12f to Color(0x99000000),  // solid behind hero title
                            0.30f to Color(0x4D000000),  // fading below hero text
                            0.45f to Color.Transparent   // fully gone before rows start
                        )
                    )
                )
        )

        // Bottom edge vignette — very subtle darkening at the extreme bottom so
        // the last row's cards don't float into pure bright backdrop. Optional,
        // keep it barely perceptible.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.85f to Color.Transparent,
                            1.0f to Color(0x66000000)
                        )
                    )
                )
        )
    }
}
