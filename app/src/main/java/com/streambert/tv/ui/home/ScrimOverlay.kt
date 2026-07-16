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
 * Static, anchored to the screen, does NOT scroll with content.
 *
 * Horizontal: dark left edge → transparent right (nav rail + hero text legibility).
 *
 * Vertical (top → bottom):
 *   - 0% (top, hero text area): ~80-90% black
 *   - ~35-40% (transition zone): fades to ~30-40% black
 *   - Rest (row area): HOLDS STEADY at ~30-40% black — backdrop bleeds through
 *     as ambient mood but stays dim enough for card focus states to remain
 *     legible (focused card = brightest thing on screen).
 */
@Composable
fun ScrimOverlay(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {

        // ── Horizontal gradient: dark left → transparent right ───────────────
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xE6000000), // ~90% black at left edge
                            0.12f to Color(0xCC000000), // strong behind nav rail
                            0.30f to Color(0x66000000), // fading
                            0.50f to Color.Transparent  // gone by mid-screen
                        )
                    )
                )
        )

        // ── Vertical gradient: dark top → steady dim behind rows ─────────────
        // Key difference from v1: does NOT fade to fully transparent.
        // Holds at ~35% black behind the rows so cards stay legible.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color(0xE0000000), // ~88% black (behind hero text)
                            0.15f to Color(0xCC000000), // ~80% (still in hero zone)
                            0.35f to Color(0x66000000), // ~40% (transition)
                            0.50f to Color(0x59000000), // ~35% (entering row area)
                            1.00f to Color(0x59000000)  // ~35% (HOLDS STEADY to bottom)
                        )
                    )
                )
        )
    }
}
