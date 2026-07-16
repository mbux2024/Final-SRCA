package com.streambert.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** A focusable poster card used inside rows. */
@Composable
fun MediaCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    onFocus: (CatalogItem) -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    Column(modifier = modifier.width(108.dp)) {
        Card(
            onClick = onClick,
            border = CardDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        3.dp, MaterialTheme.colorScheme.primary
                    )
                )
            ),
            scale = CardDefaults.scale(focusedScale = 1.3f),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
                .cardLongPress(onLongPress)
                .onFocusChanged { if (it.isFocused) onFocus(item) }
        ) {
            Box(Modifier.fillMaxSize()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Resume progress bar pinned to the bottom of the poster.
                if (progress > 0f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color(0x66000000))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
        )
    }
}

/** A titled horizontal row of media cards. */
@Composable
fun MediaRow(
    title: String,
    items: List<CatalogItem>,
    onSelect: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (CatalogItem) -> Unit = {},
    onLongPress: (CatalogItem) -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp)
        )
        MediaLazyRow(startPadding = 48.dp, itemSpacing = 16.dp) {
            itemsIndexed(
                items,
                key = { _, it -> "${it.type}_${it.id}" }
            ) { index, item ->
                MediaCard(
                    item = item,
                    onClick = { onSelect(item) },
                    onFocus = onFocus,
                    onLongPress = { onLongPress(item) },
                    focusRequester = if (index == 0) firstItemFocusRequester else null
                )
            }
        }
    }
}

/** Small rating chip (star + score). */
@Composable
fun RatingChip(rating: Double, modifier: Modifier = Modifier) {
    if (rating <= 0.0) return
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = null,
            tint = Color(0xFFFFC107),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = " ${"%.1f".format(rating)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
