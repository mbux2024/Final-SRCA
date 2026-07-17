package com.streambert.tv.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// ─────────────────────────────────────────────────────────────────────────────
// HERO_HEIGHT — only constrains the hero info block content, NOT the backdrop.
// ─────────────────────────────────────────────────────────────────────────────
private val HERO_HEIGHT = 250.dp

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
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium
                )
                Button(onClick = { viewModel.load() }, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Retry")
                }
            }

            else -> {
                HomeContent(
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
}

// ─────────────────────────────────────────────────────────────────────────────
// 4-LAYER BOX ARCHITECTURE
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
    // ── Tab state (driven by the nav rail) ───────────────────────────────────
    var currentTab by remember { mutableStateOf(HomeTab.HOME) }

    // Select rows based on the active tab
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
    // "focusedItem" is set when a row poster gains focus; null when idle.
    var focusedItem by remember { mutableStateOf<CatalogItem?>(null) }

    // Track which row has focus — used to scroll the list so previous row hides.
    // lastSnappedIndex prevents re-scrolling when horizontal focus within the
    // same row triggers onFocus again with the same index.
    var focusedRowIndex by remember { mutableStateOf(-1) }
    var lastSnappedIndex by remember { mutableStateOf(-1) }

    // LazyColumn scroll state — snap focused row to top of scrollable area.
    // ONLY fires when the row index actually CHANGES (not on horizontal moves
    // within the same row). This prevents Compose's default BringIntoView from
    // conflicting with our manual scroll.
    val listState = rememberLazyListState()
    LaunchedEffect(focusedRowIndex) {
        if (focusedRowIndex >= 0 && focusedRowIndex != lastSnappedIndex) {
            lastSnappedIndex = focusedRowIndex
            // Immediately scroll (not animate) to prevent conflict with
            // Compose's BringIntoView which runs on the same frame.
            listState.scrollToItem(index = focusedRowIndex, scrollOffset = 0)
        }
    }

    // Pool of featured titles for auto-rotation (distinct, with backdrops).
    val featuredPool = remember(rows) {
        rows.flatMap { it.items }
            .filter { it.backdropUrl != null }
            .distinctBy { "${it.type}_${it.id}" }
            .take(12)
    }

    // Determines current featured item: focus-driven or auto-rotated.
    val currentFeaturedItem = rememberFeaturedItem(
        pool = featuredPool,
        focusedItem = focusedItem,
        intervalMs = 5_000L
    )

    // Debounced extra-metadata fetch for the currently featured title.
    LaunchedEffect(currentFeaturedItem?.id, currentFeaturedItem?.type) {
        val item = currentFeaturedItem ?: return@LaunchedEffect
        delay(400)
        onHeroChanged(item)
    }

    // Long-press options target.
    var optionsItem by remember { mutableStateOf<CatalogItem?>(null) }

    // First focusable item in the LazyColumn (genre chips row).
    val firstCardFocus = remember { FocusRequester() }
    var didAutoFocus by remember { mutableStateOf(false) }
    LaunchedEffect(rows.isNotEmpty()) {
        if (rows.isNotEmpty() && !didAutoFocus) {
            didAutoFocus = true
            firstCardFocus.requestFocusAfterFrames()
        }
    }

    // ── LAYERED BOX ──────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize()) {

        // LAYER 1 — Full-screen backdrop (z-index 0)
        HeroBackdrop(
            currentFeaturedItem = currentFeaturedItem,
            modifier = Modifier.fillMaxSize()
        )

        // LAYER 2 — Scrim/gradient overlays (z-index 1)
        ScrimOverlay(modifier = Modifier.fillMaxSize())

        // LAYER 3 — Nav rail (z-index 2)
        NavRail(
            currentTab = currentTab,
            onTabSelected = { currentTab = it },
            onSearch = onSearch,
            onSettings = onSettings,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
        )

        // LAYER 4 — Content: FIXED hero info + SCROLLABLE rows
        // These are in a Column so the info block stays anchored and only
        // the LazyColumn below it scrolls. The backdrop stays full-bleed
        // behind both regions.
        Row(Modifier.fillMaxSize()) {
            // Space for nav rail
            Spacer(Modifier.width(56.dp))

            Column(Modifier.fillMaxSize()) {
                // ── FIXED: Hero info block (never scrolls) ───────────────────
                HeroInfoBlock(
                    item = currentFeaturedItem,
                    extra = currentFeaturedItem?.let {
                        state.heroExtras["${it.type}_${it.id}"]
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HERO_HEIGHT)
                )

                // ── SCROLLABLE: LazyColumn (only rows scroll) ────────────────
                // Transparent background — backdrop shows through everywhere.
                // No boundary gradient — backdrop flows seamlessly from hero to rows.
                // When focus moves to a row, scroll so that row is at the top —
                // previous row scrolls up and hides behind the hero area.
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Transparent),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    // Disable user/focus-driven scroll — only our programmatic
                    // scrollToItem controls position. This prevents Compose's
                    // default BringIntoView from fighting with our snap-to-top.
                    userScrollEnabled = false
                ) {
                    // Track absolute item indices for scroll-to-top behavior.
                    // Each row that gains focus sets focusedRowIndex to its index,
                    // triggering animateScrollToItem which snaps it to the top.
                    var nextIdx = 0

                    // ── Genre chips ──────────────────────────────────────────
                    if (currentTab != HomeTab.MY_LIST) {
                        val idx = nextIdx; nextIdx++
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
                    }

                    // ── Services (Home tab only) ─────────────────────────────
                    if (currentTab == HomeTab.HOME) {
                        val idx = nextIdx; nextIdx++
                        item(key = "services") {
                            ServicesRow(onOpenService = onOpenService)
                        }
                    }

                    // ── Continue Watching (Home tab only) ────────────────────
                    if (currentTab == HomeTab.HOME && state.continueWatching.isNotEmpty()) {
                        val idx = nextIdx; nextIdx++
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
                                    focusedRowIndex = idx
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
                    }

                    // ── Trakt watchlist (Home tab only) ──────────────────────
                    if (currentTab == HomeTab.HOME && state.traktWatchlist.isNotEmpty()) {
                        val idx = nextIdx; nextIdx++
                        item(key = "trakt_watchlist") {
                            StandardRow(
                                title = "Your Trakt Watchlist",
                                items = state.traktWatchlist,
                                onSelect = onSelect,
                                onFocus = {
                                    focusedItem = it
                                    focusedRowIndex = idx
                                },
                                onLongPress = { optionsItem = it },
                                firstItemFocusRequester = null
                            )
                        }
                    }

                    // ── Personalized recommendation rows (Home tab only) ─────
                    if (currentTab == HomeTab.HOME) {
                        val recStartIdx = nextIdx
                        state.recommendedRows.forEachIndexed { recIdx, row ->
                            val idx = recStartIdx + recIdx; nextIdx++
                            item(key = "rec_${row.title}") {
                                StandardRow(
                                    title = row.title,
                                    items = row.items,
                                    onSelect = onSelect,
                                    onFocus = {
                                        focusedItem = it
                                        focusedRowIndex = idx
                                    },
                                    onLongPress = { optionsItem = it },
                                    firstItemFocusRequester = null
                                )
                            }
                        }
                    }

                    // ── Catalog rows (tab-specific) ──────────────────────────
                    val catalogStartIdx = nextIdx
                    rows.forEachIndexed { index, row ->
                        val idx = catalogStartIdx + index
                        item(key = "${currentTab.name}_${row.title}") {
                            if (row.ranked) {
                                Top10Row(
                                    title = row.title,
                                    items = row.items,
                                    onSelect = onSelect,
                                    onFocus = {
                                        focusedItem = it
                                        focusedRowIndex = idx
                                    },
                                    onLongPress = { optionsItem = it },
                                    firstItemFocusRequester = null
                                )
                            } else {
                                StandardRow(
                                    title = row.title,
                                    items = row.items,
                                    onSelect = onSelect,
                                    onFocus = {
                                        focusedItem = it
                                        focusedRowIndex = idx
                                    },
                                    onLongPress = { optionsItem = it },
                                    firstItemFocusRequester = null
                                )
                            }
                        }
                    }

                    // ── Empty state for My List ──────────────────────────────
                    if (currentTab == HomeTab.MY_LIST && state.myList.isEmpty() && state.traktWatchlist.isEmpty()) {
                        item(key = "my_list_empty") {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "Your list is empty.\nOpen a title and choose \u201cMy List\u201d to add it.",
                                    color = Color(0xFFB5B5BE),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
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
// HERO INFO BLOCK — logo/title, metadata, description (no backdrop here).
// Positioned at the top of the LazyColumn, sized to HERO_HEIGHT.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HeroInfoBlock(
    item: CatalogItem?,
    extra: HeroExtra?,
    modifier: Modifier = Modifier
) {
    if (item == null) {
        Spacer(modifier = modifier)
        return
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomStart
    ) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp, end = 200.dp, bottom = 8.dp)
                .fillMaxWidth(0.55f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Type label
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

            // Metadata row: Genre · Year · Runtime/Episodes · Content Rating
            val metaParts = buildList {
                addAll(Genres.namesFor(item.genreIds, max = 2))
                item.year?.let { add(it) }
                extra?.episodesLabel?.let { add(it) }
                extra?.runtimeLabel?.let { add(it) }
            }
            if (metaParts.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        metaParts.joinToString("  ·  "),
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
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NAV RAIL — fixed vertical icon rail, transparent, always on top.
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
        modifier = modifier
            .width(48.dp)
            .padding(vertical = 32.dp),
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
private fun NavRailIcon(
    icon: ImageVector,
    desc: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
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
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// GENRE CHIPS ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GenreChipsRow(
    onOpenGenre: (Genre) -> Unit,
    firstItemFocusRequester: FocusRequester? = null
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(Genres.ALL, key = { _, it -> it.name }) { index, genre ->
            GenreChip(
                genre = genre,
                onClick = { onOpenGenre(genre) },
                modifier = if (index == 0 && firstItemFocusRequester != null) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                }
            )
        }
    }
}

@Composable
private fun GenreChip(genre: Genre, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        scale = CardDefaults.scale(focusedScale = 1.05f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0x44000000),
            focusedContainerColor = Color(0x88000000)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(24.dp))
        )
    ) {
        Text(
            genre.name,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SERVICES ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServicesRow(onOpenService: (StreamingService) -> Unit) {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            "Services",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp)
        )
        MediaLazyRow(startPadding = 24.dp, itemSpacing = 14.dp) {
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
