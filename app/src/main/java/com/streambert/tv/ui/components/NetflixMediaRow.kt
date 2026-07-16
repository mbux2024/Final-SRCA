package com.streambert.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Netflix/Prime-style horizontal poster row with professional focus management.
 * Uses NetflixMediaCard for each item with per-item FocusRequesters for
 * precise D-pad navigation within the row.
 */
@Composable
fun NetflixMediaRow(
    title: String,
    mediaList: List<CatalogItem>,
    onMediaClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onFocus: ((CatalogItem) -> Unit)? = null
) {
    if (mediaList.isEmpty()) return

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var currentFocusedIndex by remember { mutableStateOf(0) }
    val itemFocusRequesters = remember(mediaList.size) {
        List(minOf(mediaList.size, 20)) { FocusRequester() }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Section Title
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            ),
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp)
        )

        // LazyRow with individual focusable cards
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = true,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(mediaList) { index, media ->
                val itemFocusReq = if (index < itemFocusRequesters.size) itemFocusRequesters[index] else null

                NetflixMediaCard(
                    item = media,
                    onClick = { onMediaClick(media) },
                    onFocus = { focusedItem ->
                        onFocus?.invoke(focusedItem)
                    },
                    modifier = Modifier
                        .width(130.dp)
                        .then(
                            if (index == 0 && focusRequester != null) {
                                Modifier.focusRequester(focusRequester)
                            } else if (itemFocusReq != null) {
                                Modifier.focusRequester(itemFocusReq)
                            } else {
                                Modifier
                            }
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                currentFocusedIndex = index
                                // Scroll if item is outside visible range
                                coroutineScope.launch {
                                    val layoutInfo = listState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    if (visibleItems.isNotEmpty()) {
                                        val firstVisible = visibleItems.first().index
                                        val lastVisible = visibleItems.last().index
                                        if (index < firstVisible || index > lastVisible) {
                                            listState.scrollToItem(maxOf(0, index - 1))
                                        }
                                    }
                                }
                            }
                        }
                        .onKeyEvent { keyEvent ->
                            if (keyEvent.type == KeyEventType.KeyDown) {
                                when (keyEvent.key) {
                                    Key.DirectionUp -> {
                                        if (onNavigateUp != null) {
                                            onNavigateUp.invoke()
                                            true
                                        } else false
                                    }
                                    Key.DirectionDown -> {
                                        if (onNavigateDown != null) {
                                            onNavigateDown.invoke()
                                            true
                                        } else false
                                    }
                                    Key.DirectionLeft -> {
                                        if (index > 0) {
                                            val prevIndex = index - 1
                                            if (prevIndex < itemFocusRequesters.size) {
                                                itemFocusRequesters[prevIndex].requestFocus()
                                            }
                                            true
                                        } else {
                                            // At first item — allow focus to escape to sidebar
                                            false
                                        }
                                    }
                                    Key.DirectionRight -> {
                                        if (index < mediaList.size - 1) {
                                            val nextIndex = index + 1
                                            if (nextIndex < itemFocusRequesters.size) {
                                                itemFocusRequesters[nextIndex].requestFocus()
                                            }
                                        }
                                        true // Consume to prevent parent scroll
                                    }
                                    else -> false
                                }
                            } else false
                        }
                )
            }
        }
    }
}
