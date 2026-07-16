package com.streambert.tv.ui.service

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

/** Loads a single streaming service's catalog (movies + TV) via TMDB discover. */
class ServiceViewModel(
    private val repo: TmdbRepository,
    private val providerId: Int,
    private val serviceName: String
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState(title = serviceName))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = BrowseUiState(loading = true, title = serviceName)
        viewModelScope.launch {
            try {
                // Fire everything in parallel; each row tolerates its own failure.
                val popularMovies = async { safe { repo.moviesByProvider(providerId, "popularity.desc") } }
                val popularTv = async { safe { repo.tvByProvider(providerId, "popularity.desc") } }
                // "Only on <name>": the service's most-established (highest-voted) titles.
                val flagshipMovies = async { safe { repo.moviesByProvider(providerId, "vote_count.desc", minVotes = 300) } }
                val flagshipTv = async { safe { repo.tvByProvider(providerId, "vote_count.desc", minVotes = 200) } }
                // "New on <name>": most recent releases already out.
                val newMovies = async { safe { repo.newMoviesByProvider(providerId) } }
                val newTv = async { safe { repo.newTvByProvider(providerId) } }
                // Recommended: critically top-rated.
                val topMovies = async { safe { repo.moviesByProvider(providerId, "vote_average.desc", minVotes = 200) } }
                val topTv = async { safe { repo.tvByProvider(providerId, "vote_average.desc", minVotes = 200) } }

                val region = runCatching { repo.regionCode() }.getOrDefault("US")
                // Only the US gets the exact "In the U.S." wording; other regions
                // just say "Today".
                val where = if (region == "US") "In the U.S. " else ""

                val pm = popularMovies.await()
                val pt = popularTv.await()
                val fm = flagshipMovies.await()
                val ft = flagshipTv.await()

                val rows = buildList {
                    // Netflix-style "Top 10 … Today" (ranked #1..#10).
                    pm.take(10).takeIf { it.isNotEmpty() }?.let {
                        add(CatalogRow("Top 10 Movies ${where}Today", it, ranked = true))
                    }
                    pt.take(10).takeIf { it.isNotEmpty() }?.let {
                        add(CatalogRow("Top 10 TV Shows ${where}Today", it, ranked = true))
                    }
                    interleave(ft, fm).takeIf { it.isNotEmpty() }?.let {
                        add(CatalogRow("Only on $serviceName", it))
                    }
                    interleave(pm, pt).takeIf { it.isNotEmpty() }?.let {
                        add(CatalogRow("Popular on $serviceName", it))
                    }
                    interleave(newMovies.await(), newTv.await()).takeIf { it.isNotEmpty() }?.let {
                        add(CatalogRow("New on $serviceName", it))
                    }
                    topMovies.await().takeIf { it.isNotEmpty() }?.let { add(CatalogRow("Top Rated Movies", it)) }
                    topTv.await().takeIf { it.isNotEmpty() }?.let { add(CatalogRow("Top Rated Series", it)) }
                }

                _state.value = BrowseUiState(
                    loading = false,
                    title = serviceName,
                    hero = pm.firstOrNull() ?: pt.firstOrNull(),
                    rows = rows,
                    error = if (rows.isEmpty()) "No titles found for $serviceName in your region." else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load.")
            }
        }
    }

    /** Merge two catalogs A,B,A,B… de-duplicated, capped, for a mixed row. */
    private fun interleave(a: List<CatalogItem>, b: List<CatalogItem>, limit: Int = 20): List<CatalogItem> {
        val out = ArrayList<CatalogItem>(limit)
        val ia = a.iterator(); val ib = b.iterator()
        while (out.size < limit && (ia.hasNext() || ib.hasNext())) {
            if (ia.hasNext()) out.add(ia.next())
            if (ib.hasNext()) out.add(ib.next())
        }
        return out.distinctBy { "${it.type}_${it.id}" }
    }

    private inline fun safe(block: () -> List<CatalogItem>): List<CatalogItem> =
        try { block() } catch (e: Exception) { emptyList() }
}
