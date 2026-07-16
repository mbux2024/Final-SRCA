package com.streambert.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.ui.theme.TextPrimary
import com.streambert.tv.ui.theme.Top10Stroke

/**
 * TOP 10 row - Prime/Netflix style with huge outlined rank numbers
 * peeking out from behind each poster.
 */
@Composable
fun NetflixTop10Row(
    title: String,
    mediaList: List<CatalogItem>,
    onMediaClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocus: ((CatalogItem) -> Unit)? = null
) {
    if (mediaList.isEmpty()) return
    val items = mediaList.take(10)

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
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(items) { index, media ->
                Top10Card(
                    item = media,
                    rank = index + 1,
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
private fun Top10Card(
    item: CatalogItem,
    rank: Int,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.07f else 1f,
        tween(180),
        label = "top10_scale"
    )

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = modifier
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
    ) {
        // Giant outlined rank number, tucked behind the poster
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 130.sp,
                fontWeight = FontWeight.Black,
                color = Color.Transparent,
                drawStyle = Stroke(width = 5f)
            ),
            color = Top10Stroke,
            modifier = Modifier.offset(x = 14.dp, y = 12.dp)
        )

        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .offset(x = (-18).dp)
                .width(118.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                    else Modifier
                )
        )
    }
}
