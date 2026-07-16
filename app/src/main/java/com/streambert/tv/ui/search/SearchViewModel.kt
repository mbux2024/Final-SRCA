package com.streambert.tv.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.gemini.AiCatalog
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.search.SearchHistoryRepository
import com.streambert.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<CatalogItem> = emptyList(),
    val searched: Boolean = false,
    /** Recent searches (most recent first) — shown when the query is empty. */
    val recent: List<String> = emptyList(),
    /** Whether an AI (Gemini) key is configured — submit runs an AI search. */
    val aiAvailable: Boolean = false,
    /** True while showing AI-interpreted results (vs. plain title match). */
    val aiActive: Boolean = false,
    val header: String? = null
)

class SearchViewModel(
    private val repo: TmdbRepository,
    private val ai: AiCatalog,
    private val history: SearchHistoryRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                aiAvailable = runCatching { ai.isAvailable() }.getOrDefault(false)
            )
        }
        viewModelScope.launch {
            history.history.collect { _state.value = _state.value.copy(recent = it) }
        }
    }

    fun onQueryChange(q: String) {
        // Editing the query returns to normal (instant title) search.
        _state.value = _state.value.copy(query = q, aiActive = false, header = null)
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = _state.value.copy(results = emptyList(), searched = false, loading = false)
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // debounce keystrokes
            runSearch(q)
        }
    }

    /** Keyboard "Search" action: record the term and run the best available search. */
    fun onSubmit() {
        val q = _state.value.query.trim()
        if (q.isEmpty()) return
        record(q)
        if (_state.value.aiAvailable) {
            runAiSearch()
        } else {
            searchJob?.cancel()
            searchJob = viewModelScope.launch { runSearch(q) }
        }
    }

    /** Tapping a recent search runs it again and promotes it to the top. */
    fun onRecentSelected(term: String) {
        record(term)
        onQueryChange(term)
    }

    /** A spoken query (from the mic): record + run as an AI search when available. */
    fun onVoiceQuery(spoken: String) {
        _state.value = _state.value.copy(query = spoken)
        record(spoken)
        if (_state.value.aiAvailable) runAiSearch() else onQueryChange(spoken)
    }

    /** Record the current query — call this when the user opens a result. */
    fun recordCurrentQuery() = record(_state.value.query)

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    private fun record(q: String) {
        val trimmed = q.trim()
        if (trimmed.isNotEmpty()) viewModelScope.launch { history.add(trimmed) }
    }

    /** Interpret the current query with Gemini (mood/plot/"like X" understanding). */
    fun runAiSearch() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, aiActive = true, searched = true)
            val results = runCatching { ai.search(q) }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                loading = false,
                results = results,
                header = "AI results for \u201c$q\u201d"
            )
        }
    }

    private suspend fun runSearch(q: String) {
        _state.value = _state.value.copy(loading = true)
        val results = try {
            repo.search(q)
        } catch (e: Exception) {
            emptyList()
        }
        _state.value = _state.value.copy(loading = false, results = results, searched = true)
    }
}
