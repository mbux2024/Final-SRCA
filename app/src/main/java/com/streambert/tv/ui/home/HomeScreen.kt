package com.streambert.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.CatalogRow
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.progress.WatchProgress
import com.streambert.tv.data.tmdb.Genre
import com.streambert.tv.data.tmdb.Genres
import com.streambert.tv.data.tmdb.StreamingService
import com.streambert.tv.data.tmdb.StreamingServices
import com.streambert.tv.ui.components.ContinueWatchingRow
import com.streambert.tv.ui.components.LoadingIndicator
import com.streambert.tv.ui.components.MediaCard
import com.streambert.tv.ui.components.MediaLazyRow
import com.streambert.tv.ui.components.MediaOptionsDialog
import com.streambert.tv.ui.util.requestFocusAfterFrames
import com.streambert.tv.ui.components.StandardRow
import com.streambert.tv.ui.components.Top10Row
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

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

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            state.error != null && state.homeRows.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(state.error!!, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium)
                Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 16.dp)) { Text("Retry") }
            }
            else -> HomeContent(
                state = state,
                onSelect = onSelect,
                onResume = onResume,
                onOpenService = onOpenService,
                onOpenGenre = onOpenGenre,
                onSearch = onSearch,
                onSettings = onSettings,
                onHeroChanged = viewModel::loadHeroExtra,
                onToggleMyList = viewModel::toggleMyList,
                onMarkWatched = viewModel::markWatched,
                onMarkUnwatched = viewModel::markUnwatched
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ARCHITECTURE:
//
// Box(fillMaxSize) {
//     HeroBackdrop(...)           // FIXED — never scrolls
//     ScrimOverlay(...)           // FIXED — never scrolls
//     Row {
//         NavRail(...)            // FIXED — never scrolls
//         Column {
//             HeroInfoBlock(...)  // FIXED — crossfades, never scrolls
//             LazyColumn(...)     // ONLY this scrolls (rows only)
//         }
//     }
// }
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState,
    onSelect: (CatalogItem) -> Unit,
    onResume: (WatchProgress) -> Unit,
    onOpenService: (StreamingService) -> Unit,
    onOpenGenre: (Genre, String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onHeroChanged: (CatalogItem) -> Unit,
    onToggleMyList: (CatalogItem) -> Unit,
    onMarkWatched: (CatalogItem) -> Unit,
    onMarkUnwatched: (CatalogItem) -> Unit
) {
    // ── Tab state ────────────────────────────────────────────────────────────
    var currentTab by remember { mutableStateOf(HomeTab.HOME) }

    val rows: List<CatalogRow> = when (currentTab) {
        HomeTab.MOVIES -> state.moviesRows
        HomeTab.SHOWS -> state.showsRows
        HomeTab.MY_LIST -> buildList {
            if (state.myList.isNotEmpty()) add(CatalogRow("My List", state.myList))
            if (state.traktWatchlist.isNotEmpty()) add(CatalogRow("Trakt Watchlist", state.traktWatchlist))
        }
        else -> state.homeRows
    }

    // ── Focus-driven backdrop state ──────────────────────────────────────────
    var focusedItem by remember { mutableStateOf<CatalogItem?>(null) }

    val featuredPool = remember(rows) {
        rows.flatMap { it.items }
            .filter { it.backdropUrl != null }
            .distinctBy { "${it.type}_${it.id}" }
            .take(12)
    }

    val currentFeaturedItem = rememberFeaturedItem(
        pool = featuredPool,
        focusedItem = focusedItem,
        intervalMs = 4_500L
    )

    // Debounced hero extra metadata fetch
    LaunchedEffect(currentFeaturedItem?.id, currentFeaturedItem?.type) {
        val item = currentFeaturedItem ?: return@LaunchedEffect
        delay(400)
        onHeroChanged(item)
    }

    // Long-press options
    var optionsItem by remember { mutableStateOf<CatalogItem?>(null) }

    // LazyColumn state for scroll-to-row on focus
    val listState = rememberLazyListState()

    // Track which LazyColumn item index has focus — scroll snaps one row at a time
    var currentFocusedRowIndex by remember { mutableStateOf(0) }

    // When the focused row changes, animate scroll to that row index
    LaunchedEffect(currentFocusedRowIndex) {
        listState.animateScrollToItem(currentFocusedRowIndex)
    }

    // First focusable item
    val firstCardFocus = remember { FocusRequester() }
    var didAutoFocus by remember { mutableStateOf(false) }
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty() && !didAutoFocus) {
            didAutoFocus = true
            firstCardFocus.requestFocusAfterFrames()
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LAYERED BOX — backdrop and scrims are FIXED, NEVER scroll
    // ══════════════════════════════════════════════════════════════════════════
    Box(Modifier.fillMaxSize()) {

        // ── FIXED: Full-screen backdrop ──────────────────────────────────────
        HeroBackdrop(
            currentFeaturedItem = currentFeaturedItem,
            modifier = Modifier.fillMaxSize()
        )

        // ── FIXED: Scrim overlays ────────────────────────────────────────────
        ScrimOverlay(modifier = Modifier.fillMaxSize())

        // ── Row: NavRail (fixed) + Column(HeroInfoBlock fixed + LazyColumn scrolls)
        Row(Modifier.fillMaxSize()) {

            // ── FIXED: Nav rail ──────────────────────────────────────────────
            NavRail(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                onSearch = onSearch,
                onSettings = onSettings,
                modifier = Modifier.fillMaxHeight()
            )

            // ── Column: fixed HeroInfoBlock on top, LazyColumn below ─────────
            Column(Modifier.fillMaxSize()) {

                // ── FIXED: Hero info (crossfades, NEVER scrolls) ─────────────
                HeroInfoBlock(
                    currentFeaturedItem = currentFeaturedItem,
                    extra = currentFeaturedItem?.let {
                        state.heroExtras["${it.type}_${it.id}"]
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // ── SCROLLING: LazyColumn (ONLY content rows) ────────────────
                // Transparent background — backdrop shows through.
                // Starts directly below the fixed HeroInfoBlock.
                // Scroll snaps one row at a time via currentFocusedRowIndex.
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentPadding = PaddingValues(bottom = 48.dp)
                ) {
                    // Build a flat list of items with their absolute indices
                    // so we can pass the correct index to each row's onFocus.
                    var itemIndex = 0

                    // ── Genre chips ──────────────────────────────────────────
                    if (currentTab != HomeTab.MY_LIST) {
                        val genreIdx = itemIndex
                        item(key = "genres_${currentTab.name}") {
                            val genreMedia = when (currentTab) {
                                HomeTab.MOVIES -> "movie"
                                HomeTab.SHOWS -> "tv"
                                else -> "all"
                            }
                            GenreChipsRow(
                                onOpenGenre = { onOpenGenre(it, genreMedia) },
                                firstItemFocusRequester = firstCardFocus
                            )
                        }
                        itemIndex++
                    }

                    // ── Services (Home only) ─────────────────────────────────
                    if (currentTab == HomeTab.HOME) {
                        val svcIdx = itemIndex
                        item(key = "services") { ServicesRow(onOpenService = onOpenService) }
                        itemIndex++
                    }

                    // ── Continue Watching (Home only) ────────────────────────
                    if (currentTab == HomeTab.HOME && state.continueWatching.isNotEmpty()) {
                        val cwIdx = itemIndex
                        item(key = "continue_watching") {
                            ContinueWatchingRow(
                                entries = state.continueWatching,
                                onResume = onResume,
                                onFocus = { p ->
                                    focusedItem = CatalogItem(
                                        id = p.tmdbId, type = p.mediaType, title = p.title,
                                        overview = null, posterUrl = p.posterUrl,
                                        backdropUrl = p.backdropUrl, rating = 0.0, year = null
                                    )
                                    currentFocusedRowIndex = cwIdx
                                },
                                onLongPress = { p ->
                                    optionsItem = CatalogItem(
                                        id = p.tmdbId, type = p.mediaType, title = p.title,
                                        overview = null, posterUrl = p.posterUrl,
                                        backdropUrl = p.backdropUrl, rating = 0.0, year = null
                                    )
                                }
                            )
                        }
                        itemIndex++
                    }

                    // ── Trakt watchlist (Home only) ──────────────────────────
                    if (currentTab == HomeTab.HOME && state.traktWatchlist.isNotEmpty()) {
                        val traktIdx = itemIndex
                        item(key = "trakt_watchlist") {
                            StandardRow(
                                title = "Your Trakt Watchlist",
                                items = state.traktWatchlist,
                                onSelect = onSelect,
                                onFocus = { item ->
                                    focusedItem = item
                                    currentFocusedRowIndex = traktIdx
                                },
                                onLongPress = { optionsItem = it },
                                firstItemFocusRequester = null
                            )
                        }
                        itemIndex++
                    }

                    // ── Recommendations (Home only) ──────────────────────────
                    if (currentTab == HomeTab.HOME) {
                        val recStartIdx = itemIndex
                        items(state.recommendedRows.size, key = { "rec_${state.recommendedRows[it].title}" }) { recIdx ->
                            val row = state.recommendedRows[recIdx]
                            StandardRow(
                                title = row.title,
                                items = row.items,
                                onSelect = onSelect,
                                onFocus = { item ->
                                    focusedItem = item
                                    currentFocusedRowIndex = recStartIdx + recIdx
                                },
                                onLongPress = { optionsItem = it },
                                firstItemFocusRequester = null
                            )
                        }
                        itemIndex += state.recommendedRows.size
                    }

                    // ── Tab-specific catalog rows ────────────────────────────
                    val catalogStartIdx = itemIndex
                    itemsIndexed(rows, key = { _, it -> "${currentTab.name}_${it.title}" }) { index, row ->
                        val absoluteIdx = catalogStartIdx + index
                        if (row.ranked) {
                            Top10Row(
                                title = row.title,
                                items = row.items,
                                onSelect = onSelect,
                                onFocus = { item ->
                                    focusedItem = item
                                    currentFocusedRowIndex = absoluteIdx
                                },
                                onLongPress = { optionsItem = it },
                                firstItemFocusRequester = null
                            )
                        } else {
                            StandardRow(
                                title = row.title,
                                items = row.items,
                                onSelect = onSelect,
                                onFocus = { item ->
                                    focusedItem = item
                                    currentFocusedRowIndex = absoluteIdx
                                },
                                onLongPress = { optionsItem = it },
                                firstItemFocusRequester = null
                            )
                        }
                    }

                    // ── Empty state (My List) ────────────────────────────────
                    if (currentTab == HomeTab.MY_LIST && state.myList.isEmpty() && state.traktWatchlist.isEmpty()) {
                        item(key = "my_list_empty") {
                            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Your list is empty.\nAdd titles from the detail page.",
                                    color = Color(0xFFB5B5BE),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Options dialog ───────────────────────────────────────────────────────
    optionsItem?.let { item ->
        val inList = state.myList.any { it.id == item.id && it.type == item.type }
        MediaOptionsDialog(
            item = item,
            isInMyList = inList,
            onViewDetails = { onSelect(item) },
            onToggleLibrary = { onToggleMyList(item) },
            onMarkWatched = { onMarkWatched(item) },
            onMarkUnwatched = { onMarkUnwatched(item) },
            onDismiss = { optionsItem = null }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NAV RAIL — fixed vertical icon rail, transparent bg.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavRail(
    currentTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.width(48.dp).padding(vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NavRailIcon(Icons.Default.Search, "Search", onClick = onSearch)
        Spacer(Modifier.height(20.dp))
        NavRailIcon(Icons.Default.Home, "Home", selected = currentTab == HomeTab.HOME, onClick = { onTabSelected(HomeTab.HOME) })
        Spacer(Modifier.height(20.dp))
        NavRailIcon(Icons.Default.Movie, "Movies", selected = currentTab == HomeTab.MOVIES, onClick = { onTabSelected(HomeTab.MOVIES) })
        Spacer(Modifier.height(20.dp))
        NavRailIcon(Icons.Default.Tv, "TV Shows", selected = currentTab == HomeTab.SHOWS, onClick = { onTabSelected(HomeTab.SHOWS) })
        Spacer(Modifier.height(20.dp))
        NavRailIcon(Icons.Default.BookmarkBorder, "My List", selected = currentTab == HomeTab.MY_LIST, onClick = { onTabSelected(HomeTab.MY_LIST) })
        Spacer(Modifier.height(20.dp))
        NavRailIcon(Icons.Default.Settings, "Settings", onClick = onSettings)
    }
}

@Composable
private fun NavRailIcon(icon: ImageVector, desc: String, selected: Boolean = false, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.4f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (selected) Color(0xFFE50914) else Color.Transparent,
            focusedContainerColor = Color.White.copy(alpha = 0.2f)
        ),
        border = CardDefaults.border(border = Border.None, focusedBorder = Border.None)
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GENRE CHIPS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GenreChipsRow(onOpenGenre: (Genre) -> Unit, firstItemFocusRequester: FocusRequester? = null) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(Genres.ALL, key = { _, it -> it.name }) { index, genre ->
            Card(
                onClick = { onOpenGenre(genre) },
                modifier = if (index == 0 && firstItemFocusRequester != null) Modifier.focusRequester(firstItemFocusRequester) else Modifier,
                scale = CardDefaults.scale(focusedScale = 1.05f),
                shape = CardDefaults.shape(shape = RoundedCornerShape(24.dp)),
                colors = CardDefaults.colors(containerColor = Color(0x44000000), focusedContainerColor = Color(0x88000000)),
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(24.dp)))
            ) {
                Text(genre.name, style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SERVICES ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServicesRow(onOpenService: (StreamingService) -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text("Services", style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 24.dp, bottom = 10.dp))
        MediaLazyRow(startPadding = 24.dp, itemSpacing = 14.dp) {
            items(StreamingServices.ALL, key = { it.providerId }) { service ->
                Card(
                    onClick = { onOpenService(service) },
                    scale = CardDefaults.scale(focusedScale = 1.12f),
                    shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
                    border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))),
                    modifier = Modifier.width(150.dp).height(84.dp)
                ) {
                    Image(
                        painter = painterResource(service.logoRes), contentDescription = service.name,
                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(Color(service.brandColor))
                    )
                }
            }
        }
    }
}
