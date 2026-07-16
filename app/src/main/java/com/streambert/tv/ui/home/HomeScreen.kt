package com.streambert.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.CatalogRow
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.progress.WatchProgress
import com.streambert.tv.data.tmdb.Genre
import com.streambert.tv.data.tmdb.StreamingService
import com.streambert.tv.data.tmdb.StreamingServices
import com.streambert.tv.ui.components.CinematicHero
import com.streambert.tv.ui.components.FeaturedRow
import com.streambert.tv.ui.components.LoadingIndicator
import com.streambert.tv.ui.components.MediaOptionsDialog
import com.streambert.tv.ui.components.NetflixContinueWatchingRow
import com.streambert.tv.ui.components.NetflixMediaRow
import com.streambert.tv.ui.components.NetflixTop10Row
import com.streambert.tv.ui.components.SideNavigation
import com.streambert.tv.ui.theme.NetflixRed
import com.streambert.tv.ui.theme.PrimeBg
import com.streambert.tv.ui.theme.TextPrimary
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * Netflix/Prime-style Home Screen with:
 * - Left sidebar navigation (48dp icons)
 * - Full-page LazyColumn: Cinematic Hero at top → rows scroll underneath
 * - Continue Watching, Trending, Recommended, New Releases, Top 10, etc.
 * - Services row preserved from original
 */

enum class FocusArea {
    SIDEBAR, HERO, CONTENT
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onSelect: (CatalogItem) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onOpenService: (StreamingService) -> Unit,
    onOpenGenre: (Genre, String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var currentHeroIndex by remember { mutableStateOf(0) }
    val listState = rememberLazyListState()

    // Focus management
    val sideNavFocusRequester = remember { FocusRequester() }
    val heroPlayButtonFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }

    var currentFocusArea by remember { mutableStateOf(FocusArea.HERO) }
    var isInitialized by remember { mutableStateOf(false) }

    // Initial focus on hero play button
    LaunchedEffect(state.loading) {
        if (!state.loading && state.homeRows.isNotEmpty() && !isInitialized) {
            delay(300)
            try {
                heroPlayButtonFocusRequester.requestFocus()
                currentFocusArea = FocusArea.HERO
                isInitialized = true
                delay(150)
                listState.scrollToItem(0, 0)
            } catch (e: Exception) {
                // Fallback: focus first row
            }
        }
    }

    // Long-press options menu target
    var optionsItem by remember { mutableStateOf<CatalogItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimeBg)
    ) {
        when {
            state.loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PrimeBg)
                        .focusable(false),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LoadingIndicator()
                        Text(
                            text = "Loading Streambert...",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                }
            }

            state.error != null && state.homeRows.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PrimeBg)
                        .focusable(false),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(48.dp)
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = TextPrimary
                        )
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary.copy(alpha = 0.7f)
                        )
                        Button(onClick = { viewModel.load() }) {
                            Text("Try Again")
                        }
                    }
                }
            }

            else -> {
                // Main content layout: Sidebar + Content
                Row(modifier = Modifier.fillMaxSize()) {
                    // SIDE NAVIGATION
                    SideNavigation(
                        selectedRoute = "home",
                        onNavigate = { route ->
                            when (route) {
                                "search" -> onSearch()
                                "home" -> { /* Already on home */ }
                                "movies" -> { /* Could filter to movies tab */ }
                                "shows" -> { /* Could filter to shows tab */ }
                                "my-list" -> { /* Could filter to my list tab */ }
                                "settings" -> onSettings()
                            }
                        },
                        onNavigateToContent = {
                            currentFocusArea = FocusArea.HERO
                            try {
                                heroPlayButtonFocusRequester.requestFocus()
                            } catch (e: Exception) {
                                currentFocusArea = FocusArea.CONTENT
                                try {
                                    firstRowFocusRequester.requestFocus()
                                } catch (_: Exception) {}
                            }
                        },
                        modifier = Modifier.focusRequester(sideNavFocusRequester)
                    )

                    // Main content area - full-page LazyColumn
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .background(PrimeBg)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.Back -> {
                                            currentFocusArea = FocusArea.SIDEBAR
                                            try {
                                                sideNavFocusRequester.requestFocus()
                                            } catch (_: Exception) {}
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                    ) {
                        val heroPool = state.homeRows
                            .flatMap { it.items }
                            .filter { it.backdropUrl != null }
                            .distinctBy { "${it.type}_${it.id}" }
                            .take(10)

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimeBg),
                            userScrollEnabled = true
                        ) {
                            // ── CINEMATIC HERO ────────────────────────────────
                            if (heroPool.isNotEmpty()) {
                                item(key = "hero") {
                                    CinematicHero(
                                        mediaList = heroPool,
                                        currentIndex = currentHeroIndex % heroPool.size,
                                        heroExtras = state.heroExtras,
                                        onPlayClick = { media ->
                                            onSelect(media)
                                        },
                                        onDetailsClick = { media ->
                                            onSelect(media)
                                        },
                                        onIndexChange = { newIndex ->
                                            currentHeroIndex = newIndex
                                        },
                                        playButtonFocusRequester = heroPlayButtonFocusRequester,
                                        onNavigateDown = {
                                            currentFocusArea = FocusArea.CONTENT
                                            try {
                                                firstRowFocusRequester.requestFocus()
                                            } catch (_: Exception) {}
                                        }
                                    )
                                }
                            }

                            // ── SPACER ────────────────────────────────────────
                            item { Spacer(modifier = Modifier.height(24.dp)) }

                            // ── CONTINUE WATCHING ─────────────────────────────
                            if (state.continueWatching.isNotEmpty()) {
                                item(key = "continue_watching") {
                                    NetflixContinueWatchingRow(
                                        entries = state.continueWatching,
                                        onResume = onResume,
                                        focusRequester = firstRowFocusRequester,
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )
                                }
                            }

                            // ── SERVICES ROW (kept from original) ─────────────
                            item(key = "services") {
                                ServicesRow(
                                    onOpenService = onOpenService,
                                    firstItemFocusRequester = if (state.continueWatching.isEmpty()) firstRowFocusRequester else null
                                )
                            }

                            // ── TRENDING (all content rows from ViewModel) ────
                            // Render rows with appropriate component based on type
                            val rows = state.homeRows
                            rows.forEachIndexed { index, row ->
                                item(key = "row_${row.title}") {
                                    when {
                                        row.ranked -> {
                                            NetflixTop10Row(
                                                title = row.title,
                                                mediaList = row.items,
                                                onMediaClick = { onSelect(it) },
                                                onFocus = { viewModel.loadHeroExtra(it) },
                                                modifier = Modifier.padding(bottom = 28.dp)
                                            )
                                        }
                                        // Use FeaturedRow for "Trending" rows (landscape cards)
                                        row.title.contains("Trending", ignoreCase = true) -> {
                                            FeaturedRow(
                                                title = row.title,
                                                mediaList = row.items,
                                                onMediaClick = { onSelect(it) },
                                                onFocus = { viewModel.loadHeroExtra(it) },
                                                modifier = Modifier.padding(bottom = 28.dp)
                                            )
                                        }
                                        // Standard poster rows for everything else
                                        else -> {
                                            NetflixMediaRow(
                                                title = row.title,
                                                mediaList = row.items,
                                                onMediaClick = { onSelect(it) },
                                                onFocus = { viewModel.loadHeroExtra(it) },
                                                modifier = Modifier.padding(bottom = 24.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // ── RECOMMENDED ROWS ──────────────────────────────
                            if (state.recommendedRows.isNotEmpty()) {
                                state.recommendedRows.forEach { row ->
                                    item(key = "rec_${row.title}") {
                                        NetflixMediaRow(
                                            title = row.title,
                                            mediaList = row.items,
                                            onMediaClick = { onSelect(it) },
                                            onFocus = { viewModel.loadHeroExtra(it) },
                                            modifier = Modifier.padding(bottom = 24.dp)
                                        )
                                    }
                                }
                            }

                            // ── TRAKT WATCHLIST ────────────────────────────────
                            if (state.traktWatchlist.isNotEmpty()) {
                                item(key = "trakt_watchlist") {
                                    NetflixMediaRow(
                                        title = "Your Trakt Watchlist",
                                        mediaList = state.traktWatchlist,
                                        onMediaClick = { onSelect(it) },
                                        onFocus = { viewModel.loadHeroExtra(it) },
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )
                                }
                            }

                            // ── MY LIST ───────────────────────────────────────
                            if (state.myList.isNotEmpty()) {
                                item(key = "my_list") {
                                    NetflixMediaRow(
                                        title = "My List",
                                        mediaList = state.myList,
                                        onMediaClick = { onSelect(it) },
                                        onFocus = { viewModel.loadHeroExtra(it) },
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )
                                }
                            }

                            // ── BOTTOM PADDING ────────────────────────────────
                            item { Spacer(modifier = Modifier.height(48.dp)) }
                        }
                    }
                }
            }
        }

        // Long-press options dialog
        optionsItem?.let { item ->
            val inList = state.myList.any { it.id == item.id && it.type == item.type }
            MediaOptionsDialog(
                item = item,
                isInMyList = inList,
                onViewDetails = { onSelect(item) },
                onToggleLibrary = { viewModel.toggleMyList(item) },
                onMarkWatched = { viewModel.markWatched(item) },
                onMarkUnwatched = { viewModel.markUnwatched(item) },
                onDismiss = { optionsItem = null }
            )
        }
    }
}

// ── Services row (kept from original Final-SRCA) ─────────────────────────────

@Composable
private fun ServicesRow(
    onOpenService: (StreamingService) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            "Services",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, bottom = 10.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(StreamingServices.ALL, key = { it.providerId }) { service ->
                ServiceCard(service = service, onClick = { onOpenService(service) })
            }
        }
    }
}

@Composable
private fun ServiceCard(service: StreamingService, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.12f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        modifier = Modifier
            .width(150.dp)
            .height(84.dp)
    ) {
        Image(
            painter = painterResource(service.logoRes),
            contentDescription = service.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(Color(service.brandColor))
        )
    }
}
