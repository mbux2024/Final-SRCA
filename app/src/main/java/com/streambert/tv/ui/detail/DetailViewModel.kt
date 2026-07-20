package com.streambert.tv.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.CastPerson
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.mdblist.MDBListRepository
import com.streambert.tv.data.mdblist.RatingBadge
import com.streambert.tv.data.mylist.MyListRepository
import com.streambert.tv.data.omdb.OmdbRepository
import com.streambert.tv.data.settings.SettingsRepository
import com.streambert.tv.data.badges.BadgeRepository
import com.streambert.tv.data.badges.CompiledBadge
import com.streambert.tv.data.stream.StreamOption
import com.streambert.tv.data.stream.StreamRepository
import com.streambert.tv.data.tmdb.Episode
import com.streambert.tv.data.tmdb.SeasonSummary
import com.streambert.tv.data.tmdb.TmdbRepository
import com.streambert.tv.data.tmdb.tmdbImage
import com.streambert.tv.data.watched.WatchedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val id: Int = 0,
    val type: MediaType = MediaType.MOVIE,
    val title: String = "",
    val overview: String = "",
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val rating: Double = 0.0,
    val year: String? = null,
    val genres: List<String> = emptyList(),
    val runtimeLabel: String? = null,
    // TV only
    val seasons: List<SeasonSummary> = emptyList(),
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val episodesLoading: Boolean = false,
    val selectedEpisode: Episode? = null,
    val recommendations: List<CatalogItem> = emptyList(),
    // Sources panel
    val sourcesLoading: Boolean = false,
    val sources: List<StreamOption> = emptyList(),
    val sourcesError: String? = null,
    val sourcesTargetLabel: String = "",
    val inMyList: Boolean = false,
    val trailerKey: String? = null,
    val trailerKeys: List<String> = emptyList(),
    val imdbRating: Double? = null,
    val mdbRatings: List<RatingBadge> = emptyList(),
    val watchedKeys: Set<String> = emptySet(),
    val cast: List<CastPerson> = emptyList(),
    /** Compiled NuvioTV-style badge filters; matched against each source title. */
    val badgeFilters: List<CompiledBadge> = emptyList(),
    val error: String? = null
)

class DetailViewModel(
    private val repo: TmdbRepository,
    private val streams: StreamRepository,
    private val myList: MyListRepository,
    private val omdb: OmdbRepository,
    private val mdblist: MDBListRepository,
    private val watchedRepo: WatchedRepository,
    private val settings: SettingsRepository,
    private val badges: BadgeRepository,
    private val type: MediaType,
    private val id: Int
) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState(type = type))
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    // Cached IMDb id so the sources panel doesn't re-resolve it per episode.
    private var imdbId: String? = null
    private var imdbResolved = false

    init {
        load()
        observeMyList()
        observeWatched()
        loadBadgeFilters()
    }

    private fun loadBadgeFilters() {
        viewModelScope.launch {
            val filters = runCatching { badges.filters() }.getOrDefault(emptyList())
            if (filters.isNotEmpty()) {
                _state.value = _state.value.copy(badgeFilters = filters)
            }
        }
    }

    private fun observeWatched() {
        viewModelScope.launch {
            watchedRepo.watched.collect { keys ->
                _state.value = _state.value.copy(watchedKeys = keys)
            }
        }
    }

    /** Toggle watched for the current target: the movie, or the selected episode. */
    fun toggleWatched() {
        val s = _state.value
        val key = if (type == MediaType.MOVIE) {
            WatchedRepository.movieKey(id)
        } else {
            val ep = s.selectedEpisode ?: return
            WatchedRepository.episodeKey(id, s.selectedSeason, ep.episodeNumber)
        }
        viewModelScope.launch { watchedRepo.toggle(key) }
    }

    /** Toggle watched for a specific episode (from the episode list). */
    fun toggleEpisodeWatched(episode: Episode) {
        val key = WatchedRepository.episodeKey(id, _state.value.selectedSeason, episode.episodeNumber)
        viewModelScope.launch { watchedRepo.toggle(key) }
    }

    private fun observeMyList() {
        viewModelScope.launch {
            myList.contains(type, id).collect { saved ->
                _state.value = _state.value.copy(inMyList = saved)
            }
        }
    }

    /** Add/remove this title from My List. */
    fun toggleMyList() {
        val s = _state.value
        viewModelScope.launch {
            myList.toggle(
                CatalogItem(
                    id = id,
                    type = type,
                    title = s.title,
                    overview = s.overview,
                    posterUrl = s.posterUrl,
                    backdropUrl = s.backdropUrl,
                    rating = s.rating,
                    year = s.year
                )
            )
        }
    }

    private fun load() {
        viewModelScope.launch {
            try {
                val base: DetailUiState = if (type == MediaType.MOVIE) {
                    val d = repo.movieDetails(id)
                    DetailUiState(
                        loading = false,
                        id = id,
                        type = MediaType.MOVIE,
                        title = d.title ?: "",
                        overview = d.overview ?: "",
                        backdropUrl = tmdbImage(d.backdropPath, "w1280"),
                        posterUrl = tmdbImage(d.posterPath, "w500"),
                        rating = d.voteAverage,
                        year = d.year,
                        genres = d.genres.map { it.name },
                        runtimeLabel = d.runtime?.let { "${it} min" }
                    )
                } else {
                    val d = repo.tvDetails(id)
                    val realSeasons = d.seasons.filter { it.seasonNumber > 0 }
                    val first = realSeasons.firstOrNull()?.seasonNumber ?: 1
                    DetailUiState(
                        loading = false,
                        id = id,
                        type = MediaType.TV,
                        title = d.name ?: "",
                        overview = d.overview ?: "",
                        backdropUrl = tmdbImage(d.backdropPath, "w1280"),
                        posterUrl = tmdbImage(d.posterPath, "w500"),
                        rating = d.voteAverage,
                        year = d.year,
                        genres = d.genres.map { it.name },
                        runtimeLabel = "${d.numberOfSeasons} season(s)",
                        seasons = realSeasons,
                        selectedSeason = first
                    )
                }
                _state.value = base

                if (type == MediaType.TV) {
                    selectSeason(base.selectedSeason)
                } else {
                    // Movie: load its sources immediately for the side panel.
                    loadSources(season = null, episode = null, targetLabel = base.title)
                }
                loadRecommendations()
                loadTrailer()
                loadImdbRating()
                loadMdbRatings()
                loadCast()
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = e.message ?: "Failed to load details."
                )
            }
        }
    }

    fun selectSeason(season: Int) {
        _state.value = _state.value.copy(
            selectedSeason = season,
            episodesLoading = true,
            selectedEpisode = null
        )
        viewModelScope.launch {
            val eps = try {
                repo.seasonDetails(id, season).episodes
            } catch (e: Exception) {
                emptyList()
            }
            _state.value = _state.value.copy(episodes = eps, episodesLoading = false)
            // Auto-select the first episode so the panel has something to show.
            eps.firstOrNull()?.let { selectEpisode(it) }
        }
    }

    /** Select an episode (TV): highlights it and loads its sources. */
    fun selectEpisode(ep: Episode) {
        _state.value = _state.value.copy(selectedEpisode = ep)
        loadSources(
            season = _state.value.selectedSeason,
            episode = ep.episodeNumber,
            targetLabel = "S${_state.value.selectedSeason} · E${ep.episodeNumber}"
        )
    }

    private fun loadSources(season: Int?, episode: Int?, targetLabel: String) {
        _state.value = _state.value.copy(
            sourcesLoading = true,
            sources = emptyList(),
            sourcesError = null,
            sourcesTargetLabel = targetLabel
        )
        viewModelScope.launch {
            val imdb = ensureImdb()
            if (imdb.isNullOrBlank()) {
                _state.value = _state.value.copy(
                    sourcesLoading = false,
                    sourcesError = "Couldn't find an IMDb id for this title."
                )
                return@launch
            }
            // Use incremental loading — UI updates progressively as each provider
            // returns results, without waiting for all to finish.
            streams.listStreamsFlow(imdb, season, episode).collect { loadingState ->
                _state.value = _state.value.copy(
                    sourcesLoading = !loadingState.allDone,
                    sources = loadingState.sources,
                    sourcesError = when {
                        loadingState.allDone && loadingState.sources.isEmpty() ->
                            loadingState.error ?: "No cached sources found yet."
                        else -> null
                    }
                )
            }
        }
    }

    private suspend fun ensureImdb(): String? {
        if (imdbResolved) return imdbId
        imdbId = repo.imdbId(id, type)
        imdbResolved = true
        return imdbId
    }

    private fun loadTrailer() {
        viewModelScope.launch {
            if (!settings.currentTmdbEnrichEnabled() || !settings.currentTmdbUseTrailers()) return@launch
            val keys = repo.trailerYoutubeKeys(id, type)
            if (keys.isNotEmpty()) _state.value = _state.value.copy(trailerKeys = keys, trailerKey = keys.first())
        }
    }

    private fun loadImdbRating() {
        viewModelScope.launch {
            val imdb = ensureImdb() ?: return@launch
            val rating = omdb.imdbRating(imdb)
            if (rating != null) {
                _state.value = _state.value.copy(imdbRating = rating)
            } else {
                // Enrich: fall back to MDBList's IMDb rating when no OMDb key/result.
                val mdbImdb = runCatching { mdblist.imdbRating(id, type) }.getOrNull()
                if (mdbImdb != null) _state.value = _state.value.copy(imdbRating = mdbImdb)
            }
        }
    }

    private fun loadMdbRatings() {
        viewModelScope.launch {
            val badges = runCatching { mdblist.ratings(id, type) }.getOrDefault(emptyList())
            if (badges.isNotEmpty()) _state.value = _state.value.copy(mdbRatings = badges)
        }
    }

    private fun loadCast() {
        viewModelScope.launch {
            if (!settings.currentTmdbEnrichEnabled() || !settings.currentTmdbUseCredits()) return@launch
            val people = repo.credits(id, type)
            if (people.isNotEmpty()) _state.value = _state.value.copy(cast = people)
        }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            if (!settings.currentTmdbEnrichEnabled() || !settings.currentTmdbUseMoreLikeThis()) return@launch
            val item = CatalogItem(
                id = id, type = type, title = _state.value.title,
                overview = null, posterUrl = null, backdropUrl = null,
                rating = 0.0, year = null
            )
            val recs = try { repo.recommendationsFor(item) } catch (e: Exception) { emptyList() }
            _state.value = _state.value.copy(recommendations = recs)
        }
    }
}
