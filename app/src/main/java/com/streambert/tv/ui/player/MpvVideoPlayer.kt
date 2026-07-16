package com.streambert.tv.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import com.streambert.tv.ui.util.requestFocusAfterFrames
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambert.tv.data.stream.SubtitleTrack
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen MPV (libmpv) player with a Netflix-style D-pad control overlay.
 * MPV handles the exotic HDR/DV/HEVC/DTS content that ExoPlayer can't render.
 */
@Composable
fun MpvVideoPlayer(
    url: String,
    title: String,
    startPositionMs: Long,
    subtitles: List<SubtitleTrack>,
    subtitleScale: Float,
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    onSubtitleStyleChange: (SubtitleStyle) -> Unit = {},
    prefs: PlaybackPrefs = PlaybackPrefs(),
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?
) {
    var view by remember(url) { mutableStateOf<MpvPlayerView?>(null) }
    // Live subtitle style, applied to mpv and persisted upward.
    var style by remember { mutableStateOf(subtitleStyle) }

    // Apply subtitle style to mpv whenever it changes (JNI writes off-main-thread).
    LaunchedEffect(view, style, subtitleScale) {
        val v = view ?: return@LaunchedEffect
        withContext(Dispatchers.IO) {
            v.applySubtitleScale(subtitleScale * 16.6f * style.fontPercent / 100f)
            v.setSubtitleBold(style.bold)
            v.setSubtitleColor(style.textColor)
            v.setSubtitleOutline(style.outline)
            v.setSubtitleDelay(style.delayMs / 1000.0)
        }
    }

    // Persist progress periodically while playing (reads mpv off the main thread).
    LaunchedEffect(view) {
        val v = view ?: return@LaunchedEffect
        while (true) {
            delay(10_000)
            val (pos, dur) = withContext(Dispatchers.IO) { v.currentPositionMs() to v.durationMs() }
            if (dur > 0 && pos > 0) onSaveProgress(pos, dur)
        }
    }

    DisposableEffect(url) {
        onDispose {
            view?.let { v ->
                // Snapshot last position before releasing (reads are on the disposing
                // thread; release is delegated to mpv's own worker via destroy()).
                val dur = v.durationMs()
                val pos = v.currentPositionMs()
                if (dur > 0 && pos > 0) onSaveProgress(pos, dur)
                v.releasePlayer()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MpvPlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    hardwareDecodeMode = prefs.hwdecMode
                    ensureInitialized()
                    setMedia(url, startPositionMs)
                    applySubtitleScale(subtitleScale * 16.6f) // ~fraction → mpv sub-scale
                    subtitles.forEach { addExternalSubtitle(it.url, it.displayLanguage, it.language) }
                    setPreferredLanguages(prefs.preferredAudioLang, prefs.preferredSubtitleLang)
                    view = this
                }
            }
        )

        view?.let {
            MpvControls(
                view = it,
                title = title,
                style = style,
                onStyleChange = { s -> style = s; onSubtitleStyleChange(s) },
                autoplayNext = prefs.autoplayNextEnabled,
                skipIntro = prefs.skipIntroEnabled && prefs.isSeries,
                onBack = onBack,
                onNextEpisode = onNextEpisode
            )
        }
    }
}

private enum class MpvPicker { NONE, SUBTITLES, AUDIO, SPEED }

/** Immutable snapshot of the mpv playback state, read off the main thread. */
private data class PollSnapshot(
    val position: Long,
    val duration: Long,
    val playing: Boolean,
    val resolution: String?
)

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

@Composable
private fun MpvControls(
    view: MpvPlayerView,
    title: String,
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    autoplayNext: Boolean,
    skipIntro: Boolean,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?
) {
    var visible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var picker by remember { mutableStateOf(MpvPicker.NONE) }
    var interactions by remember { mutableStateOf(0) }
    var upNextDismissed by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(-1) }
    var resolution by remember { mutableStateOf<String?>(null) }
    var speed by remember { mutableStateOf(1.0f) }
    var zoomed by remember { mutableStateOf(false) }

    // "Up Next" as credits start: last ~40s of the episode (but not the very end).
    val nearCredits = duration > 0 && position >= duration - CREDITS_THRESHOLD_MS && (duration - position) > 2000
    val showUpNext = autoplayNext && onNextEpisode != null && nearCredits && !upNextDismissed
    LaunchedEffect(showUpNext) {
        if (showUpNext) {
            for (c in UP_NEXT_COUNTDOWN downTo 1) { countdown = c; delay(1000) }
            countdown = 0
            onNextEpisode?.invoke()
        } else {
            countdown = -1
        }
    }

    // "Skip Intro" button window (heuristic, since we have no exact intro markers).
    val showSkipIntro = skipIntro && duration > 0 && position in SKIP_INTRO_MIN_MS..SKIP_INTRO_MAX_MS

    val scope = rememberCoroutineScope()

    // Poll playback state. CRITICAL: the mpv_get_property JNI calls are read on a
    // background dispatcher. Doing them on the main thread blocks input dispatch
    // and triggers "Input dispatching timed out" ANRs when the decoder is busy
    // (e.g. software-decoding 4K/HDR), which is exactly what the crash logs showed.
    LaunchedEffect(view) {
        while (true) {
            val snap = withContext(Dispatchers.IO) {
                PollSnapshot(
                    position = view.currentPositionMs(),
                    duration = view.durationMs(),
                    playing = view.isPlayingNow(),
                    resolution = if (resolution == null) view.mediaBadges().resolution else resolution
                )
            }
            position = snap.position
            duration = snap.duration
            isPlaying = snap.playing
            if (resolution == null) snap.resolution?.let { resolution = it }
            delay(1000)
        }
    }

    LaunchedEffect(visible, isPlaying, interactions, picker) {
        if (visible && isPlaying && picker == MpvPicker.NONE) {
            delay(5000)
            visible = false
        }
    }

    fun reveal() { visible = true; interactions++ }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) playFocus.requestFocusAfterFrames()
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xB3000000),
                            0.25f to Color.Transparent,
                            0.7f to Color.Transparent,
                            1f to Color(0xCC000000)
                        )
                    )
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                }

                // NuvioTV-style layout: title above a full-width seek bar, then a
                // row split into left (transport) and right (track) controls.
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 28.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(Modifier.padding(top = 14.dp))
                    MpvProgressBar(position, duration)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left: transport controls.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MpvButton(Icons.Filled.Replay10, "Rewind 10s") {
                                reveal()
                                val target = (position - 10_000).coerceAtLeast(0)
                                scope.launch(Dispatchers.IO) { view.seekToMs(target) }
                            }
                            MpvButton(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.focusRequester(playFocus)
                            ) {
                                reveal()
                                val nowPaused = isPlaying
                                isPlaying = !nowPaused
                                scope.launch(Dispatchers.IO) { view.setPaused(nowPaused) }
                            }
                            MpvButton(Icons.Filled.Forward10, "Forward 10s") {
                                reveal()
                                val end = if (duration > 0) duration else Long.MAX_VALUE
                                val target = (position + 10_000).coerceAtMost(end)
                                scope.launch(Dispatchers.IO) { view.seekToMs(target) }
                            }
                            if (onNextEpisode != null) {
                                MpvButton(Icons.Filled.SkipNext, "Next episode") { reveal(); onNextEpisode() }
                            }
                        }
                        // Right: track selection, speed, aspect, resolution.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            resolution?.let { ResolutionChip(it) }
                            MpvButton(Icons.Filled.Speed, "Playback speed") { reveal(); picker = MpvPicker.SPEED }
                            MpvButton(Icons.Filled.AspectRatio, if (zoomed) "Fit" else "Zoom") {
                                reveal()
                                zoomed = !zoomed
                                val z = zoomed
                                scope.launch(Dispatchers.IO) { view.setPanscan(if (z) 1.0 else 0.0) }
                            }
                            MpvButton(Icons.Filled.Subtitles, "Subtitles") { reveal(); picker = MpvPicker.SUBTITLES }
                            MpvButton(Icons.Filled.Audiotrack, "Audio") { reveal(); picker = MpvPicker.AUDIO }
                        }
                    }
                }
            }
        }

        if (picker == MpvPicker.SUBTITLES) {
            MpvSubtitlesPanel(
                view = view,
                style = style,
                onStyleChange = onStyleChange,
                onDismiss = { picker = MpvPicker.NONE; reveal() }
            )
        } else if (picker == MpvPicker.AUDIO) {
            MpvTrackPicker(
                view = view,
                picker = picker,
                onDismiss = { picker = MpvPicker.NONE; reveal() }
            )
        }
        if (picker == MpvPicker.SPEED) {
            MpvSpeedPicker(
                current = speed,
                onSelect = { s -> speed = s; scope.launch(Dispatchers.IO) { view.setPlaybackSpeed(s) }; picker = MpvPicker.NONE; reveal() },
                onDismiss = { picker = MpvPicker.NONE; reveal() }
            )
        }

        // Skip Intro button (bottom-end).
        if (showSkipIntro && picker == MpvPicker.NONE) {
            Button(
                onClick = { scope.launch(Dispatchers.IO) { view.seekToMs(SKIP_INTRO_TARGET_MS) } },
                colors = ButtonDefaults.colors(containerColor = Color(0xE6FFFFFF), contentColor = Color.Black),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 56.dp)
            ) {
                Text("Skip Intro", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }
        }

        // "Up Next" countdown card (bottom-end) as the credits start.
        if (showUpNext && countdown >= 0 && picker == MpvPicker.NONE) {
            UpNextCard(
                seconds = countdown,
                onPlayNow = { onNextEpisode?.invoke() },
                onCancel = { upNextDismissed = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 48.dp, bottom = 48.dp)
            )
        }

        if (!visible && picker == MpvPicker.NONE) {
            MpvRevealCatcher(onReveal = { reveal() })
        }
    }
}

private const val CREDITS_THRESHOLD_MS = 40_000L
private const val UP_NEXT_COUNTDOWN = 10
private const val SKIP_INTRO_MIN_MS = 5_000L
private const val SKIP_INTRO_MAX_MS = 100_000L
private const val SKIP_INTRO_TARGET_MS = 100_000L

@Composable
private fun UpNextCard(
    seconds: Int,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier
            .background(Color(0xF2141414), RoundedCornerShape(12.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Next episode in ${seconds}s",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onPlayNow,
                colors = ButtonDefaults.colors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text("Play now", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp, end = 8.dp))
                }
            }
            Button(
                onClick = onCancel,
                colors = ButtonDefaults.colors(containerColor = Color(0x33FFFFFF), contentColor = Color.White)
            ) {
                Text("Cancel", modifier = Modifier.padding(horizontal = 10.dp))
            }
        }
    }
}

@Composable
private fun MpvButton(
    icon: ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.padding(horizontal = 4.dp)) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(30.dp))
    }
}

/** Small pill showing the current resolution (4K/1080p/…) in the control bar. */
@Composable
private fun ResolutionChip(label: String) {
    Box(
        Modifier
            .padding(horizontal = 6.dp)
            .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Black)
    }
}



@Composable
private fun MpvProgressBar(position: Long, duration: Long) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(formatTimeMpv(position), style = MaterialTheme.typography.labelMedium, color = Color.White)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
                .height(5.dp)
                .background(Color(0x55FFFFFF), RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(5.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
            )
        }
        Text(formatTimeMpv(duration), style = MaterialTheme.typography.labelMedium, color = Color.White)
    }
}

@Composable
private fun MpvTrackPicker(
    view: MpvPlayerView,
    picker: MpvPicker,
    onDismiss: () -> Unit
) {
    val wantAudio = picker == MpvPicker.AUDIO
    val scope = rememberCoroutineScope()
    // readTracks() is a burst of synchronous JNI reads — do it off the main thread.
    val tracks by produceState(initialValue = emptyList<MpvTrack>(), picker) {
        value = withContext(Dispatchers.IO) {
            view.readTracks().filter { if (wantAudio) it.type == "audio" else it.type == "sub" }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.34f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Text(
                if (wantAudio) "Audio" else "Subtitles",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!wantAudio) {
                    item {
                        MpvTrackRow("Off", selected = tracks.none { it.selected }) {
                            scope.launch(Dispatchers.IO) { view.disableSubtitles() }; onDismiss()
                        }
                    }
                }
                items(tracks) { t ->
                    val label = if (t.codec != null) "${t.label} (${t.codec})" else t.label
                    MpvTrackRow(label, selected = t.selected) {
                        scope.launch(Dispatchers.IO) {
                            if (wantAudio) view.selectAudioTrack(t.id) else view.selectSubtitleTrack(t.id)
                        }
                        onDismiss()
                    }
                }
                if (tracks.isEmpty()) {
                    item {
                        Text(
                            "No tracks available.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

/** Nuvio-style subtitle panel for the MPV engine (delay works natively here). */
@Composable
private fun MpvSubtitlesPanel(
    view: MpvPlayerView,
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    var tick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val subs by produceState(initialValue = emptyList<MpvTrack>(), tick) {
        value = withContext(Dispatchers.IO) { view.readTracks().filter { it.type == "sub" } }
    }
    val anySelected = subs.any { it.selected }

    val entries = subs.mapIndexed { idx, t ->
        SubtitleEntry(
            key = "msub_${t.id}_$idx",
            source = if (t.external) "SubSense" else "Built in",
            language = mpvDisplayLang(t.lang),
            subId = t.label,
            selected = t.selected,
            onSelect = { scope.launch(Dispatchers.IO) { view.selectSubtitleTrack(t.id) }; tick++ }
        )
    }
    val languages = buildList {
        add(SubtitleLanguage("None", 0, !anySelected) {
            scope.launch(Dispatchers.IO) { view.disableSubtitles() }; tick++
        })
        subs.groupBy { mpvDisplayLang(it.lang) }.forEach { (name, tracks) ->
            add(SubtitleLanguage(name, tracks.size, tracks.any { it.selected }) {
                scope.launch(Dispatchers.IO) { view.selectSubtitleTrack(tracks.first().id) }; tick++
            })
        }
    }

    SubtitleStylePanel(
        languages = languages,
        subtitles = entries,
        style = style,
        onStyleChange = onStyleChange,
        supportsDelay = true,
        onDismiss = onDismiss
    )
}

private fun mpvDisplayLang(code: String?): String {
    if (code.isNullOrBlank() || code == "und") return "Unknown"
    return runCatching { Locale(code).displayLanguage }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
}

@Composable
private fun MpvSpeedPicker(current: Float, onSelect: (Float) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.34f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Text(
                "Playback speed",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SPEED_OPTIONS) { s ->
                    val label = if (s == 1.0f) "Normal (1x)" else "${s}x"
                    MpvTrackRow(label = label, selected = s == current) { onSelect(s) }
                }
            }
        }
    }
}

@Composable
private fun MpvTrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.03f),
        border = CardDefaults.border(
            focusedBorder = Border(
                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            )
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selected) "\u25CF  $label" else label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun MpvRevealCatcher(onReveal: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .focusable()
            .onKeyEvent { ev ->
                // BACK must fall through so it exits the player (one level back).
                when {
                    ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK -> false
                    ev.type == KeyEventType.KeyDown -> { onReveal(); true }
                    else -> false
                }
            }
    )
}

private fun formatTimeMpv(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
