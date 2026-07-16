package com.streambert.tv.ui.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.CatalogRow
import com.streambert.tv.data.tmdb.TmdbRepository
import com.streambert.tv.ui.browse.BrowseUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads a genre's catalog (movies + TV) via TMDB discover with_genres. */
class GenreViewModel(
    private val repo: TmdbRepository,
    private val genreName: String,
    private val movieGenreId: Int,
    private val tvGenreId: Int,
    /** "movie" restricts to movies, "tv" to shows, anything else = both. */
    private val mediaFilter: String = "all"
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState(title = genreName))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private val includeMovies = mediaFilter != "tv"
    private val includeTv = mediaFilter != "movie"

    init { load() }

    fun load() {
        _state.value = BrowseUiState(loading = true, title = genreName)
        viewModelScope.launch {
            try {
                val movies = if (includeMovies && movieGenreId > 0)
                    async { safe { repo.moviesByGenre(movieGenreId) } } else null
                val tv = if (includeTv && tvGenreId > 0)
                    async { safe { repo.tvByGenre(tvGenreId) } } else null
                val topMovies = if (includeMovies && movieGenreId > 0)
                    async { safe { repo.moviesByGenre(movieGenreId, "vote_average.desc", minVotes = 200) } } else null
                val topTv = if (includeTv && tvGenreId > 0)
                    async { safe { repo.tvByGenre(tvGenreId, "vote_average.desc", minVotes = 200) } } else null

                val m = movies?.await().orEmpty()
                val t = tv?.await().orEmpty()

                val rows = buildList {
                    if (m.isNotEmpty()) add(CatalogRow("$genreName Movies", m))
                    if (t.isNotEmpty()) add(CatalogRow("$genreName TV Shows", t))
                    topMovies?.await()?.takeIf { it.isNotEmpty() }?.let { add(CatalogRow("Top Rated $genreName Movies", it)) }
                    topTv?.await()?.takeIf { it.isNotEmpty() }?.let { add(CatalogRow("Top Rated $genreName TV Shows", it)) }
                }

                _state.value = BrowseUiState(
                    loading = false,
                    title = genreName,
                    hero = m.firstOrNull() ?: t.firstOrNull(),
                    rows = rows,
                    error = if (rows.isEmpty()) "No $genreName titles found." else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load.")
            }
        }
    }

    private inline fun safe(block: () -> List<CatalogItem>): List<CatalogItem> =
        try { block() } catch (e: Exception) { emptyList() }
}
