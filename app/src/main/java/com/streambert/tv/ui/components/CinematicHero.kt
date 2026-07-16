package com.streambert.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.tmdb.Genres
import com.streambert.tv.ui.home.HeroExtra
import com.streambert.tv.ui.theme.BadgeOutline
import com.streambert.tv.ui.theme.NetflixRed
import com.streambert.tv.ui.theme.PrimeBg
import com.streambert.tv.ui.theme.PrimeBgDeep
import com.streambert.tv.ui.theme.PrimeBlue
import com.streambert.tv.ui.theme.PrimeSurface
import com.streambert.tv.ui.theme.PrimeTextDim
import com.streambert.tv.ui.theme.RatingGold
import com.streambert.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * CINEMATIC HERO - Prime Video layout with Netflix slide behavior.
 *
 * Full-bleed backdrop with auto-advance carousel, Prime-style left gradient
 * and bottom fade into the page background, metadata badges, and focusable
 * Play / More Info actions. Uses TMDB backdrop images (no video preview).
 */
@Composable
fun CinematicHero(
    mediaList: List<CatalogItem>,
    currentIndex: Int,
    heroExtras: Map<String, HeroExtra>,
    onPlayClick: (CatalogItem) -> Unit,
    onDetailsClick: (CatalogItem) -> Unit,
    onIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    playButtonFocusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null
) {
    if (mediaList.isEmpty()) return
    val safeIndex = currentIndex.coerceIn(0, mediaList.size - 1)
    val currentMedia = mediaList[safeIndex]
    val extra = heroExtras["${currentMedia.type}_${currentMedia.id}"]

    // Auto-advance every 15 seconds
    LaunchedEffect(safeIndex, mediaList.size) {
        if (mediaList.size > 1) {
            delay(15_000)
            onIndexChange((safeIndex + 1) % mediaList.size)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(500.dp)
    ) {
        // Backdrop with crossfade
        Crossfade(
            targetState = safeIndex,
            animationSpec = tween(durationMillis = 900),
            label = "hero_bg"
        ) { idx ->
            val media = mediaList.getOrElse(idx) { currentMedia }
            AsyncImage(
                model = media.backdropUrl ?: media.posterUrl,
                contentDescription = media.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Prime-style gradient: strong left panel + fade to page bg at bottom
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            PrimeBgDeep.copy(alpha = 0.94f),
                            PrimeBgDeep.copy(alpha = 0.55f),
                            Color.Transparent
                        ),
                        endX = 1400f
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, PrimeBg),
                        startY = 750f
                    )
                )
        )

        // Content column (left-aligned like Prime)
        val contentAlpha = remember { mutableStateOf(false) }
        LaunchedEffect(currentMedia.id) {
            contentAlpha.value = false
            delay(250)
            contentAlpha.value = true
        }
        val alpha by animateFloatAsState(
            targetValue = if (contentAlpha.value) 1f else 0f,
            animationSpec = tween(700), label = "hero_content"
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, bottom = 88.dp, end = 500.dp)
                .graphicsLayer { this.alpha = alpha },
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Brand strip
            Text(
                text = "STREAMBERT",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = NetflixRed,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp
                )
            )

            // Title
            Text(
                text = currentMedia.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.Black,
                    color = TextPrimary
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // "Newly Added" teal accent
            Text(
                text = "Newly Added",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = Color(0xFF4FD8CE),
                    fontWeight = FontWeight.SemiBold
                )
            )

            // Metadata row: rating, year, runtime, genres, cert badge, quality badge
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rating
                val rating = extra?.imdbRating ?: currentMedia.rating.takeIf { it > 0.0 }
                if (rating != null && rating > 0.0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "\u2605",
                            color = RatingGold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            String.format("%.1f", rating),
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
                // Year
                currentMedia.year?.let {
                    Text(it, color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                }
                // Runtime or episodes
                extra?.runtimeLabel?.let {
                    Text(it, color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                }
                extra?.episodesLabel?.let {
                    Text(it, color = PrimeTextDim, style = MaterialTheme.typography.titleSmall)
                }
                // Genres
                val genreNames = Genres.namesFor(currentMedia.genreIds, max = 2)
                if (genreNames.isNotEmpty()) {
                    Text(
                        genreNames.joinToString(" \u2022 "),
                        color = PrimeTextDim,
                        style = MaterialTheme.typography.titleSmall
                    )
                }
                // Content rating badge
                CertBadge(extra?.contentRating ?: "PG-13")
            }

            // Description
            currentMedia.overview?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = TextPrimary.copy(alpha = 0.92f),
                        lineHeight = 24.sp
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Actions: Play + More Info
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                HeroActionButton(
                    label = "Play",
                    icon = { Icon(Icons.Default.PlayArrow, null, Modifier.size(26.dp)) },
                    primary = true,
                    focusRequester = playButtonFocusRequester,
                    onClick = { onPlayClick(currentMedia) },
                    onNavigateDown = onNavigateDown,
                    onNavigateLeft = {
                        if (mediaList.size > 1) onIndexChange(
                            if (safeIndex > 0) safeIndex - 1 else mediaList.size - 1
                        )
                    },
                    onNavigateRight = null
                )
                HeroActionButton(
                    label = "More Info",
                    icon = { Icon(Icons.Default.Info, null, Modifier.size(20.dp)) },
                    primary = false,
                    onClick = { onDetailsClick(currentMedia) },
                    onNavigateDown = onNavigateDown,
                    onNavigateLeft = null,
                    onNavigateRight = {
                        if (mediaList.size > 1) onIndexChange((safeIndex + 1) % mediaList.size)
                    }
                )
            }
        }

        // Slide dots (bottom-center)
        if (mediaList.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                mediaList.forEachIndexed { i, _ ->
                    Box(
                        Modifier
                            .size(
                                width = if (i == safeIndex) 22.dp else 7.dp,
                                height = 7.dp
                            )
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (i == safeIndex) TextPrimary
                                else TextPrimary.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
    }
}

/** Content rating badge (e.g. "PG-13", "R", "TV-MA") */
@Composable
fun CertBadge(text: String) {
    Box(
        Modifier
            .border(1.dp, BadgeOutline, RoundedCornerShape(3.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = PrimeTextDim,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
        )
    }
}

/**
 * Prime-style pill button: white at rest (primary) or dark surface;
 * Prime blue when focused.
 */
@Composable
fun HeroActionButton(
    label: String,
    icon: @Composable () -> Unit,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> if (primary) Color.White else PrimeBlue
        primary -> Color.White.copy(alpha = 0.9f)
        else -> PrimeSurface.copy(alpha = 0.85f)
    }
    val fg = when {
        focused -> if (primary) Color.Black else Color.White
        primary -> Color.Black
        else -> TextPrimary
    }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(150), label = "btn_scale")

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.Enter, Key.DirectionCenter -> {
                            onClick(); true
                        }
                        Key.DirectionDown -> {
                            onNavigateDown?.invoke() != null
                        }
                        Key.DirectionLeft -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft(); true
                            } else false
                        }
                        Key.DirectionRight -> {
                            if (onNavigateRight != null) {
                                onNavigateRight(); true
                            } else false
                        }
                        Key.DirectionUp -> true // consume - nothing above hero
                        else -> {
                            if (keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER) {
                                onClick(); true
                            } else false
                        }
                    }
                } else false
            }
            .focusable()
            .clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.tv.material3.LocalContentColor provides fg
            ) { icon() }
            Text(
                label,
                color = fg,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}
