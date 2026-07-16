package com.streambert.tv.ui.search

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.CircularProgressIndicator
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.ui.components.SideNavigation
import com.streambert.tv.ui.theme.NetflixRed
import com.streambert.tv.ui.theme.PrimeBg
import com.streambert.tv.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * Netflix/Prime-style Search Screen with:
 * - Left sidebar navigation
 * - Left panel: virtual QWERTY keyboard + recent searches
 * - Right panel: search results grid or top searches
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSelect: (CatalogItem) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var searchQuery by remember { mutableStateOf(state.query) }

    // Sync local query with ViewModel
    LaunchedEffect(searchQuery) {
        viewModel.onQueryChange(searchQuery)
    }

    // Focus requester for the first keyboard key
    val keyboardFocusRequester = remember { FocusRequester() }

    // Auto-focus keyboard on screen load
    LaunchedEffect(Unit) {
        delay(400)
        try {
            keyboardFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // QWERTY Virtual keyboard layout
    val keyboardRows = listOf(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("z", "x", "c", "v", "b", "n", "m"),
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    )

    // TV-optimized layout
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
    ) {
        // SIDE NAVIGATION (48dp width)
        SideNavigation(
            selectedRoute = "search",
            onNavigate = { route ->
                when (route) {
                    "search" -> { /* Already on search */ }
                    "home" -> onBack()
                    else -> onBack()
                }
            },
            onNavigateToContent = {
                try {
                    keyboardFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        )

        // Main Content Area
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // LEFT PANEL - Virtual Keyboard
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(16.dp)
            ) {
                // Search Input Display
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Search..." else searchQuery,
                            color = if (searchQuery.isEmpty()) Color.Gray else Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Virtual Keyboard
                Column(
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    keyboardRows.forEachIndexed { rowIndex, row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            row.forEachIndexed { keyIndex, key ->
                                VirtualKey(
                                    key = key,
                                    onClick = { searchQuery += key },
                                    modifier = Modifier.weight(1f),
                                    focusRequester = if (rowIndex == 0 && keyIndex == 0) keyboardFocusRequester else null
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                    }

                    // Special keys row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Space key
                        VirtualKey(
                            key = "space",
                            onClick = { searchQuery += " " },
                            modifier = Modifier.weight(2f),
                            isSpecial = true
                        )

                        // Backspace key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Gray.copy(alpha = 0.3f))
                                .focusable()
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyDown &&
                                        (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                                            keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER)
                                    ) {
                                        if (searchQuery.isNotEmpty()) {
                                            searchQuery = searchQuery.dropLast(1)
                                        }
                                        true
                                    } else false
                                }
                                .clickable {
                                    if (searchQuery.isNotEmpty()) {
                                        searchQuery = searchQuery.dropLast(1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Backspace,
                                contentDescription = "Backspace",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Recent Searches
                if (state.recent.isNotEmpty() && searchQuery.isEmpty()) {
                    Text(
                        text = "Recent",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    state.recent.take(5).forEach { term ->
                        RecentItem(
                            term = term,
                            onClick = { searchQuery = term }
                        )
                    }
                }
            }

            // RIGHT PANEL - Results Grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                when {
                    searchQuery.isEmpty() -> {
                        Text(
                            text = "Search Movies & TV Shows",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 24.dp)
                        )
                        Text(
                            text = "Use the keyboard to search for your favorite movies and shows.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }

                    state.loading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = NetflixRed)
                        }
                    }

                    state.searched && state.results.isEmpty() -> {
                        Text(
                            text = "No results for \u201c$searchQuery\u201d",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            modifier = Modifier.padding(top = 48.dp)
                        )
                    }

                    state.results.isNotEmpty() -> {
                        state.header?.let {
                            Text(
                                text = it,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 136.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(state.results, key = { "${it.type}_${it.id}" }) { item ->
                                SearchResultCard(
                                    item = item,
                                    onClick = {
                                        viewModel.recordCurrentQuery()
                                        onSelect(item)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VirtualKey(
    key: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSpecial: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.5f else 1.0f,
        animationSpec = tween(durationMillis = 150),
        label = "virtual_key_scale"
    )

    Box(
        modifier = modifier
            .height(32.dp)
            .scale(scale)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isFocused -> Color.White.copy(alpha = 0.3f)
                    isSpecial -> Color.Gray.copy(alpha = 0.5f)
                    else -> Color.Gray.copy(alpha = 0.3f)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (key == "space") "SPACE" else key.uppercase(),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun RecentItem(
    term: String,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .padding(vertical = 6.dp, horizontal = 12.dp)
            .background(
                color = if (isFocused) NetflixRed.copy(alpha = 0.8f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = term,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "search_card_scale"
    )

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown &&
                    (keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                        keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER)
                ) {
                    onClick()
                    true
                } else false
            }
            .clickable { onClick() }
            .then(
                if (isFocused) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                else Modifier
            )
    ) {
        // Poster
        AsyncImage(
            model = item.posterUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )

        // Title at bottom (always visible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    ),
                    RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                )
                .padding(8.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
