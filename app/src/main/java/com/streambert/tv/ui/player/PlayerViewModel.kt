package com.streambert.tv.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.progress.ProgressRepository
import com.streambert.tv.data.progress.WatchProgress
import com.streambert.tv.data.settings.SettingsRepository
import com.streambert.tv.data.stream.StreamRepository
import com.streambert.tv.data.stream.StreamResolution
import com.streambert.tv.data.stream.SubtitleRepository
import com.streambert.tv.data.stream.SubtitleTrack
import com.streambert.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlayerUiState {
    data class Resolving(val message: String) : PlayerUiState
    data class Ready(
        val url: String,
        val title: String,
        val startPositionMs: Long,
        val tunnelingEnabled: Boolean,
        val subtitles: List<SubtitleTrack> = emptyList(),
        val subtitleScale: Float = 0.06f,
        val subtitleStyle: SubtitleStyle = SubtitleStyle(),
        val playerEngine: String = SettingsRepository.ENGINE_EXOPLAYER,
        val prefs: PlaybackPrefs = PlaybackPrefs(),
        val hasNextEpisode: Boolean = false
    ) : PlayerUiState
    data class Error(val message: String) : PlayerUiState
}

/** Playback preferences applied to the active engine. */
data class PlaybackPrefs(
    val hwdecMode: String = "auto-safe",
    val preferredAudioLang: String = "none",
    val preferredSubtitleLang: String = "none",
    val autoplayNextEnabled: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val isSeries: Boolean = false
)

/**
 * Resolves a playable stream for the requested TMDB title via the configured
 * Stremio addon (Torrentio/Comet + TorBox), then exposes it to the player.
 * Also handles resume: it loads any saved position and persists progress as
 * the user watches. Movies pass season/episode = -1.
 */
class PlayerViewModel(
    private val tmdb: TmdbRepository,
    private val stream: StreamRepository,
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
    private val subtitles: SubtitleRepository,
    private val type: MediaType,
    private val id: Int,
    private val season: Int,
    private val episode: Int,
    private val title: String,
    private val posterUrl: String = "",
    private val backdropUrl: String = "",
    private val directUrl: String = "",
    private val directHash: String = "",
    private val directDebrid: String = ""
) : ViewModel() {

    private val _state = MutableStateFlow<PlayerUiState>(PlayerUiState.Resolving("Starting…"))
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val progressKey = WatchProgress.keyFor(type, id, season, episode)

    // Guard so we only auto-switch ExoPlayer -> MPV once per resolved stream.
    private var triedMpvFallback = false

    // The exact source currently playing, persisted so resume replays it.
    private var playingUrl: String = ""
    private var playingHash: String = ""

    // The next episode to roll Continue Watching forward to once this one
    // finishes (null for movies or the final episode of a series).
    private data class NextEp(val season: Int, val episode: Int)
    private var nextEpisode: NextEp? = null
    private var nextUpEnqueued = false

    init { resolve() }

    fun resolve() {
        triedMpvFallback = false
        viewModelScope.launch {
            // ── FAST PATH: Load settings + compute engine in parallel with nothing blocking ──
            val startMs = progress.get(progressKey)?.positionMs ?: 0L
            val tunneling = settings.currentTunnelingEnabled()
            val engine = when (settings.currentPlayerEngine()) {
                SettingsRepository.ENGINE_AUTO ->
                    if (runCatching { tmdb.isAnime(id, type) }.getOrDefault(false))
                        SettingsRepository.ENGINE_MPV else SettingsRepository.ENGINE_EXOPLAYER
                SettingsRepository.ENGINE_MPV -> SettingsRepository.ENGINE_MPV
                else -> SettingsRepository.ENGINE_EXOPLAYER
            }
            val prefs = PlaybackPrefs(
                hwdecMode = if (settings.currentMpvHardwareDecoding()) "auto-safe" else "no",
                preferredAudioLang = settings.currentPreferredAudioLanguage(),
                preferredSubtitleLang = settings.currentPreferredSubtitleLanguage(),
                autoplayNextEnabled = settings.currentAutoplayNextEnabled(),
                skipIntroEnabled = settings.currentSkipIntroEnabled(),
                isSeries = season > 0
            )
            val subScale = settings.currentSubtitleFraction()
            val subStyle = SubtitleStyle(
                delayMs = settings.currentSubtitleDelayMs(),
                fontPercent = settings.currentSubtitleFontPercent(),
                bold = settings.currentSubtitleBold(),
                textColor = settings.currentSubtitleTextColor(),
                opacityPercent = settings.currentSubtitleTextOpacityPercent(),
                outline = settings.currentSubtitleOutline()
            )
            val s = season.takeIf { it > 0 }
            val e = episode.takeIf { it > 0 }

            // ── PARALLEL: compute next episode in background (don't block playback) ──
            val nextEpDeferred = if (season > 0) kotlinx.coroutines.async { computeNextEpisode() } else null

            // ── FAST PATH: if we have a direct URL, start immediately ──
            if (directUrl.isNotBlank()) {
                playingUrl = directUrl
                playingHash = directHash
                nextEpisode = nextEpDeferred?.await()
                val hasNext = nextEpisode != null
                _state.value = PlayerUiState.Ready(directUrl, title.ifBlank { "Stream" }, startMs, tunneling, emptyList(), subScale, subStyle, engine, prefs, hasNext)
                // Fetch subtitles in background AFTER playback starts
                launchSubtitleFetch(s, e)
                return@launch
            }

            // ── FAST PATH: if we have a hash, resolve it immediately ──
            if (directHash.isNotBlank()) {
                _state.value = PlayerUiState.Resolving("Preparing stream…")
                when (val r = stream.resolveHash(directHash, title, s, e, directDebrid.ifBlank { null })) {
                    is StreamResolution.Ready -> {
                        playingUrl = r.url
                        playingHash = directHash
                        nextEpisode = nextEpDeferred?.await()
                        val hasNext = nextEpisode != null
                        _state.value = PlayerUiState.Ready(r.url, title.ifBlank { r.label }, startMs, tunneling, emptyList(), subScale, subStyle, engine, prefs, hasNext)
                        // Fetch subtitles in background AFTER playback starts
                        launchSubtitleFetch(s, e)
                    }
                    is StreamResolution.Failure -> _state.value = PlayerUiState.Error(r.message)
                    is StreamResolution.Progress -> _state.value = PlayerUiState.Resolving(r.message)
                }
                return@launch
            }

            // ── AUTO-RESOLVE: find best stream (this is the slow path) ──
            _state.value = PlayerUiState.Resolving("Finding best source…")

            // Resolve IMDb ID (needed for stream lookup)
            val imdb = tmdb.imdbId(id, type)
            if (imdb.isNullOrBlank()) {
                _state.value = PlayerUiState.Error("Couldn't find an IMDb id for this title.")
                return@launch
            }

            // Resolve stream — this is the main network call
            when (val result = stream.resolveStream(
                imdbId = imdb,
                season = s,
                episode = e,
                onProgress = { msg -> _state.value = PlayerUiState.Resolving(msg) }
            )) {
                is StreamResolution.Ready -> {
                    playingUrl = result.url
                    playingHash = ""
                    nextEpisode = nextEpDeferred?.await()
                    val hasNext = nextEpisode != null
                    _state.value = PlayerUiState.Ready(result.url, title.ifBlank { result.label }, startMs, tunneling, emptyList(), subScale, subStyle, engine, prefs, hasNext)
                    // Fetch subtitles in background AFTER playback starts
                    launchSubtitleFetch(s, e)
                }
                is StreamResolution.Failure ->
                    _state.value = PlayerUiState.Error(result.message)
                is StreamResolution.Progress ->
                    _state.value = PlayerUiState.Resolving(result.message)
            }
        }
    }

    /**
     * Fetches subtitles in background and updates the Ready state when they arrive.
     * This runs AFTER playback has already started, so it doesn't delay video.
     */
    private fun launchSubtitleFetch(season: Int?, episode: Int?) {
        viewModelScope.launch {
            val imdb = tmdb.imdbId(id, type)
            if (imdb.isNullOrBlank()) return@launch
            val subs = runCatching { subtitles.fetch(imdb, season, episode) }.getOrDefault(emptyList())
            if (subs.isNotEmpty()) {
                val cur = _state.value
                if (cur is PlayerUiState.Ready) {
                    _state.value = cur.copy(subtitles = subs)
                }
            }
        }
    }

    /**
     * Next episode within the current season, or the first episode of the next
     * season, using TMDB per-season episode counts. Null for the series finale.
     */
    private suspend fun computeNextEpisode(): NextEp? {
        val details = runCatching { tmdb.tvDetails(id) }.getOrNull() ?: return null
        val seasons = details.seasons.filter { it.seasonNumber > 0 }
        val current = seasons.firstOrNull { it.seasonNumber == season } ?: return null
        return when {
            episode < current.episodeCount -> NextEp(season, episode + 1)
            seasons.any { it.seasonNumber == season + 1 && it.episodeCount > 0 } -> NextEp(season + 1, 1)
            else -> null
        }
    }

    /** Update + persist subtitle style from the player's subtitle panel. */
    fun updateSubtitleStyle(style: SubtitleStyle) {
        val cur = _state.value
        if (cur is PlayerUiState.Ready) _state.value = cur.copy(subtitleStyle = style)
        viewModelScope.launch {
            settings.setSubtitleDelayMs(style.delayMs)
            settings.setSubtitleFontPercent(style.fontPercent)
            settings.setSubtitleBold(style.bold)
            settings.setSubtitleTextColor(style.textColor)
            settings.setSubtitleTextOpacityPercent(style.opacityPercent)
            settings.setSubtitleOutline(style.outline)
        }
    }

    /**
     * Surface a fatal playback error (from the ExoPlayer listener) so the UI
     * shows a readable message + Retry instead of a silent black screen.
     */
    fun onPlaybackError(message: String) {
        _state.value = PlayerUiState.Error(message)
    }

    /**
     * ExoPlayer hit a decoder-capability error (e.g. the device can't decode 4K
     * HEVC / Dolby Vision). Transparently switch this same stream to the MPV
     * (libmpv) engine, which software-decodes those formats. Falls through to a
     * normal error only if we've already tried MPV.
     */
    fun onDecoderUnsupported(message: String) {
        val cur = _state.value
        if (cur is PlayerUiState.Ready &&
            cur.playerEngine != SettingsRepository.ENGINE_MPV &&
            !triedMpvFallback
        ) {
            triedMpvFallback = true
            _state.value = cur.copy(playerEngine = SettingsRepository.ENGINE_MPV)
        } else {
            _state.value = PlayerUiState.Error(message)
        }
    }

    /** Persist the current playback position (called periodically + on exit). */
    fun saveProgress(positionMs: Long, durationMs: Long) {
        if (durationMs <= 0 || positionMs <= 0) return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            progress.save(
                WatchProgress(
                    key = progressKey,
                    type = type.tmdb,
                    tmdbId = id,
                    title = title.ifBlank { "Stream" },
                    posterUrl = posterUrl.ifBlank { null },
                    backdropUrl = backdropUrl.ifBlank { null },
                    season = season,
                    episode = episode,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    // Remember the exact source so resume replays it (prefer the
                    // torrent hash, which re-resolves a fresh non-expiring URL).
                    streamUrl = playingUrl.ifBlank { null },
                    streamHash = playingHash.ifBlank { null },
                    updatedAt = now
                )
            )

            // Once a series episode is essentially finished, roll Continue
            // Watching forward to the next episode (NuvioTV-style "next up").
            val finished = positionMs.toFloat() / durationMs >= FINISHED_FRACTION
            if (finished && season > 0 && !nextUpEnqueued) {
                nextEpisode?.let { n ->
                    nextUpEnqueued = true
                    progress.save(
                        WatchProgress(
                            key = WatchProgress.keyFor(type, id, n.season, n.episode),
                            type = type.tmdb,
                            tmdbId = id,
                            title = title.ifBlank { "Stream" },
                            posterUrl = posterUrl.ifBlank { null },
                            backdropUrl = backdropUrl.ifBlank { null },
                            season = n.season,
                            episode = n.episode,
                            positionMs = 0,
                            durationMs = 0,
                            nextUp = true,
                            updatedAt = now + 1 // sort just above the finished one
                        )
                    )
                }
            }
        }
    }

    private companion object {
        const val FINISHED_FRACTION = 0.92f
    }
}
