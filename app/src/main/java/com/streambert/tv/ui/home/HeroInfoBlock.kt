package com.streambert.tv.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.tmdb.Genres

/**
 * FIXED LAYER — Hero text/metadata block.
 *
 * Positioned at a fixed location over the backdrop (top-left area).
 * Crossfades content to match the currently-focused item.
 * NEVER scrolls — it is NOT inside the LazyColumn.
 */
@Composable
fun HeroInfoBlock(
    currentFeaturedItem: CatalogItem?,
    extra: HeroExtra?,
    modifier: Modifier = Modifier
) {
    // Crossfade the entire text block when the featured item changes
    Crossfade(
        targetState = currentFeaturedItem,
        animationSpec = tween(durationMillis = 500),
        label = "hero_info_crossfade",
        modifier = modifier
    ) { item ->
        if (item == null) {
            Box(Modifier.fillMaxWidth())
        } else {
            HeroInfoContent(item = item, extra = extra)
        }
    }
}

@Composable
private fun HeroInfoContent(
    item: CatalogItem,
    extra: HeroExtra?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .padding(start = 24.dp, top = 40.dp, end = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Type label (SERIES / FILM)
        Text(
            text = if (item.type == MediaType.TV) "S E R I E S" else "F I L M",
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFFB9B9C2),
            fontWeight = FontWeight.Black
        )

        // Title
        Text(
            text = item.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 38.sp
        )

        // Metadata: Genre · Year · Runtime/Episodes · Content Rating
        val meta = buildList {
            addAll(Genres.namesFor(item.genreIds, max = 2))
            item.year?.let { add(it) }
            extra?.episodesLabel?.let { add(it) }
            extra?.runtimeLabel?.let { add(it) }
        }
        if (meta.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    meta.joinToString("  ·  "),
                    color = Color(0xFFCFCFCF),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                extra?.contentRating?.takeIf { it.isNotBlank() }?.let { rating ->
                    Box(
                        Modifier
                            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            rating,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // IMDb rating badge
        extra?.imdbRating?.takeIf { it > 0.0 }?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFFF5C518))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        "IMDb",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Text(
                    "  ${String.format(java.util.Locale.US, "%.1f", r)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Description
        item.overview?.takeIf { it.isNotBlank() }?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFDDDDDD),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}
