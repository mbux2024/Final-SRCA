package com.streambert.tv.ui.trailer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.trailer.TrailerStream
import com.streambert.tv.data.trailer.YouTubeExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TrailerUiState {
    data object Loading : TrailerUiState
    data class Ready(val stream: TrailerStream) : TrailerUiState
    data object Error : TrailerUiState
}

/**
 * Resolves a YouTube trailer id (from TMDB) into a direct, natively-playable
 * stream via [YouTubeExtractor], so the player doesn't rely on the WebView
 * IFrame player (which fails on many Android TV devices).
 */
class TrailerViewModel(
    private val extractor: YouTubeExtractor,
    private val videoId: String
) : ViewModel() {

    private val _state = MutableStateFlow<TrailerUiState>(TrailerUiState.Loading)
    val state: StateFlow<TrailerUiState> = _state.asStateFlow()

    init { resolve() }

    fun resolve() {
        _state.value = TrailerUiState.Loading
        viewModelScope.launch {
            // videoId may be a comma-separated list of candidate ids (best first).
            val candidates = videoId.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val stream = extractor.extractFirst(candidates)
            _state.value = if (stream != null) TrailerUiState.Ready(stream) else TrailerUiState.Error
        }
    }
}
