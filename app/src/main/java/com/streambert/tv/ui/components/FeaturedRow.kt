package com.streambert.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.ui.theme.PrimeTextDim
import com.streambert.tv.ui.theme.TextPrimary

/**
 * FEATURED ROW - Prime Video style 16:9 landscape cards with a bottom
 * gradient and title, for showcase sections like "New Releases".
 */
@Composable
fun FeaturedRow(
    title: String,
    mediaList: List<CatalogItem>,
    onMediaClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: ((CatalogItem) -> Unit)? = null
) {
    if (mediaList.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 56.dp, bottom = 12.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(mediaList) { index, media ->
                FeaturedCard(
                    item = media,
                    onClick = { onMediaClick(media) },
                    onFocus = { onFocus?.invoke(media) },
                    modifier = if (index == 0 && focusRequester != null)
                        Modifier.focusRequester(focusRequester) else Modifier
                )
            }
        }
    }
}

@Composable
private fun FeaturedCard(
    item: CatalogItem,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.06f else 1f,
        tween(180),
        label = "featured_scale"
    )

    Box(
        modifier = modifier
            .width(268.dp)
            .aspectRatio(16f / 9f)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER)
                ) {
                    onClick(); true
                } else false
            }
            .focusable()
            .clickable { onClick() }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier
            )
    ) {
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Bottom gradient + title (Prime card style)
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                        startY = 180f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.year?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimeTextDim
                    )
                }
                if (item.rating > 0) {
                    Text(
                        "\u2605 ${String.format("%.1f", item.rating)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = PrimeTextDim
                    )
                }
            }
        }
    }
}
