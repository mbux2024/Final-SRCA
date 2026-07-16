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
import androidx.compose.runtime.mutableLongStateOf
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
 * LAYER 1 — Full-screen crossfading backdrop with Ken Burns effect.
 *
 * - Fills the ENTIRE screen (Modifier.fillMaxSize()), NOT constrained by
 *   HERO_HEIGHT. Sits behind everything else; never scrolls.
 * - Crossfade (~700ms) whenever [currentFeaturedItem] changes.
 * - Subtle Ken Burns scale animation (1.0 → 1.05, 12s cycle).
 */
@Composable
fun HeroBackdrop(
    currentFeaturedItem: CatalogItem?,
    modifier: Modifier = Modifier
) {
    // Ken Burns: subtle infinite scale 1.0 → 1.05 → 1.0 (12s full cycle)
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
 * Focus/rotation logic: determines the current featured item for the backdrop.
 *
 * - When [focusedItem] is non-null → the backdrop shows that item (debounced
 *   by 200ms to avoid flicker when scrubbing).
 * - When [focusedItem] is null (idle) → auto-rotates through [pool] every
 *   [intervalMs] (~4-5s).
 * - A 2s cooldown after focus clears before auto-rotation resumes.
 */
@Composable
fun rememberFeaturedItem(
    pool: List<CatalogItem>,
    focusedItem: CatalogItem?,
    intervalMs: Long = 4_500L
): CatalogItem? {
    var rotationIndex by remember { mutableIntStateOf(0) }
    var lastFocusClearTime by remember { mutableLongStateOf(0L) }

    // Debounced focus: wait 200ms before committing the focused item to the
    // backdrop so that fast D-pad scrubbing doesn't cause rapid flickering.
    var debouncedFocus by remember { mutableStateOf<CatalogItem?>(null) }
    LaunchedEffect(focusedItem) {
        if (focusedItem != null) {
            delay(200L) // debounce
            debouncedFocus = focusedItem
        } else {
            debouncedFocus = null
            lastFocusClearTime = System.currentTimeMillis()
        }
    }

    // Auto-rotate when idle (no focus)
    LaunchedEffect(debouncedFocus, pool.size) {
        if (debouncedFocus != null || pool.isEmpty()) return@LaunchedEffect
        // Cooldown after focus clears
        val elapsed = System.currentTimeMillis() - lastFocusClearTime
        if (elapsed < 2000L && lastFocusClearTime > 0L) {
            delay(2000L - elapsed)
        }
        while (true) {
            delay(intervalMs)
            rotationIndex = (rotationIndex + 1) % pool.size
        }
    }

    return when {
        debouncedFocus != null -> debouncedFocus
        pool.isNotEmpty() -> pool.getOrNull(rotationIndex % pool.size)
        else -> null
    }
}
