package com.streambert.tv.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.model.CatalogRow
import com.streambert.tv.data.tmdb.TmdbRepository
import com.streambert.tv.ui.browse.BrowseUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Loads a person's movie + TV filmography for the cast → filmography screen. */
class PersonViewModel(
    private val repo: TmdbRepository,
    private val personId: Int,
    private val personName: String
) : ViewModel() {

    private val _state = MutableStateFlow(BrowseUiState(loading = true, title = personName))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = BrowseUiState(loading = true, title = personName)
        viewModelScope.launch {
            try {
                // Prefer the freshly-fetched name; fall back to the one passed in nav.
                val name = repo.personName(personId).ifBlank { personName }
                val filmography = repo.personFilmography(personId)
                val movies = filmography.filter { it.type == com.streambert.tv.data.model.MediaType.MOVIE }
                val shows = filmography.filter { it.type == com.streambert.tv.data.model.MediaType.TV }
                val rows = buildList {
                    if (movies.isNotEmpty()) add(CatalogRow("Movies", movies))
                    if (shows.isNotEmpty()) add(CatalogRow("TV Shows", shows))
                }
                _state.value = BrowseUiState(
                    loading = false,
                    title = name,
                    hero = filmography.firstOrNull { it.backdropUrl != null } ?: filmography.firstOrNull(),
                    rows = rows,
                    error = if (rows.isEmpty()) "No filmography found for $name." else null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load.")
            }
        }
    }
}
