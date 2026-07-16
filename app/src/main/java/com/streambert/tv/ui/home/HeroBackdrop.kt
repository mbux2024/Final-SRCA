package com.streambert.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import kotlinx.coroutines.delay

/**
 * LAYER 1 — Full-screen crossfading backdrop with Ken Burns effect and
 * auto-rotation timer.
 *
 * Fills the ENTIRE screen (Modifier.fillMaxSize()), sits behind everything.
 * Crossfades (~700ms) whenever [currentFeaturedItem] changes.
 * Applies a subtle Ken Burns scale (1.0 → 1.05) over 12s per backdrop.
 */
@Composable
fun HeroBackdrop(
    currentFeaturedItem: CatalogItem?,
    modifier: Modifier = Modifier
) {
    // Ken Burns infinite scale animation (subtle 1.0 → 1.05 → 1.0)
    val infiniteTransition = rememberInfiniteTransition(label = "ken_burns")
    val kenBurnsScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ken_burns_scale"
    )

    Crossfade(
        targetState = currentFeaturedItem?.backdropUrl ?: currentFeaturedItem?.posterUrl,
        animationSpec = tween(durationMillis = 700),
        label = "backdrop_crossfade",
        modifier = modifier.fillMaxSize()
    ) { imageUrl ->
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = kenBurnsScale
                    scaleY = kenBurnsScale
                }
        )
    }
}

/**
 * Manages the auto-rotation logic for the featured item.
 *
 * - Rotates through [pool] every [intervalMs] when [focusedItem] is null (idle).
 * - When [focusedItem] is non-null, auto-rotation pauses; the backdrop shows
 *   the focused item.
 * - Returns the current featured item (focus-driven or auto-rotated).
 */
@Composable
fun rememberFeaturedItem(
    pool: List<CatalogItem>,
    focusedItem: CatalogItem?,
    intervalMs: Long = 5_000L
): CatalogItem? {
    var rotationIndex by remember { mutableIntStateOf(0) }
    // Track when focus last cleared to add a brief cooldown before resuming auto-rotate.
    var lastFocusClearTime by remember { mutableStateOf(0L) }

    // Auto-rotate when idle (no focus)
    LaunchedEffect(focusedItem, pool.size) {
        if (focusedItem != null || pool.isEmpty()) return@LaunchedEffect
        // Small cooldown after focus clears before resuming rotation
        val now = System.currentTimeMillis()
        if (now - lastFocusClearTime < 2000L && lastFocusClearTime > 0L) {
            delay(2000L - (now - lastFocusClearTime))
        }
        while (true) {
            delay(intervalMs)
            rotationIndex = (rotationIndex + 1) % pool.size
        }
    }

    // Track when focus is cleared
    LaunchedEffect(focusedItem) {
        if (focusedItem == null) {
            lastFocusClearTime = System.currentTimeMillis()
        }
    }

    return when {
        focusedItem != null -> focusedItem
        pool.isNotEmpty() -> pool.getOrNull(rotationIndex % pool.size)
        else -> null
    }
}
