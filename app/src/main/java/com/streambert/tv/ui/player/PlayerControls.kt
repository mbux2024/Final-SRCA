package com.streambert.tv.ui.player

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
import androidx.compose.material.icons.filled.Audiotrack
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import com.streambert.tv.ui.util.requestFocusAfterFrames
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.Locale
import kotlinx.coroutines.delay

/** A user-selectable subtitle/audio track. */
private data class TrackChoice(
    val group: TrackGroup,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean
)

/**
 * Netflix-style custom control overlay drawn on top of the video surface.
 * Handles play/pause, ±10s, subtitle & audio pickers, next-episode (TV),
 * a title line, a progress bar with times, and a badge that announces
 * HDR / Dolby Vision / Dolby Atmos / DTS:X when detected in the stream.
 */
@Composable
fun PlayerControls(
    player: Player,
    title: String,
    sideloaded: List<com.streambert.tv.data.stream.SubtitleTrack> = emptyList(),
    subtitleStyle: SubtitleStyle = SubtitleStyle(),
    onSubtitleStyleChange: (SubtitleStyle) -> Unit = {},
    autoplayNext: Boolean = true,
    skipIntro: Boolean = false,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var picker by remember { mutableStateOf(Picker.NONE) }
    var interactions by remember { mutableStateOf(0) } // bumped to reset auto-hide
    var resolution by remember { mutableStateOf<String?>(null) }
    var upNextDismissed by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(-1) }

    // "Up Next" as credits start: last ~40s (but not the very end).
    val nearCredits = duration > 0 && position >= duration - 40_000L && (duration - position) > 2000
    val showUpNext = autoplayNext && onNextEpisode != null && nearCredits && !upNextDismissed
    LaunchedEffect(showUpNext) {
        if (showUpNext) {
            for (c in 10 downTo 1) { countdown = c; delay(1000) }
            countdown = 0
            onNextEpisode?.invoke()
        } else countdown = -1
    }
    val showSkipIntro = skipIntro && duration > 0 && position in 5_000L..100_000L

    // React to player state + track changes.
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onTracksChanged(tracks: Tracks) {
                detectResolution(tracks)?.let { resolution = it }
            }
        }
        player.addListener(listener)
        resolution = detectResolution(player.currentTracks)
        onDispose { player.removeListener(listener) }
    }

    // Poll position/duration.
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    // Auto-hide the controls after a few seconds of no interaction while playing.
    LaunchedEffect(visible, isPlaying, interactions, picker) {
        if (visible && isPlaying && picker == Picker.NONE) {
            delay(5000)
            visible = false
        }
    }

    fun reveal() { visible = true; interactions++ }

    val playFocus = remember { FocusRequester() }
    // Move focus onto the transport when controls appear so the D-pad works.
    LaunchedEffect(visible) {
        if (visible) playFocus.requestFocusAfterFrames()
    }

    Box(modifier.fillMaxSize()) {
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
                // Top bar: back.
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
                ) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                }

                // Bottom cluster: progress + title + transport row.
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp, vertical = 24.dp)
                ) {
                    ProgressBar(position = position, duration = duration)
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlButton(Icons.Filled.Replay10, "Rewind 10s") {
                            reveal(); player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        }
                        ControlButton(
                            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.focusRequester(playFocus)
                        ) {
                            reveal(); if (isPlaying) player.pause() else player.play()
                        }
                        ControlButton(Icons.Filled.Forward10, "Forward 10s") {
                            reveal()
                            val end = if (duration > 0) duration else Long.MAX_VALUE
                            player.seekTo((player.currentPosition + 10_000).coerceAtMost(end))
                        }

                        Box(Modifier.width(20.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        if (onNextEpisode != null) {
                            ControlButton(Icons.Filled.SkipNext, "Next episode") {
                                reveal(); onNextEpisode()
                            }
                        }
                        resolution?.let { ResolutionChip(it) }
                        ControlButton(Icons.Filled.Subtitles, "Subtitles") {
                            reveal(); picker = Picker.SUBTITLES
                        }
                        ControlButton(Icons.Filled.Audiotrack, "Audio") {
                            reveal(); picker = Picker.AUDIO
                        }
                    }
                }
            }
        }

        // Track picker panels. Subtitles use the full Nuvio-style panel; audio
        // keeps the simple list.
        if (picker == Picker.SUBTITLES) {
            SubtitlesPanel(
                player = player,
                sideloaded = sideloaded,
                style = subtitleStyle,
                onStyleChange = onSubtitleStyleChange,
                onDismiss = { picker = Picker.NONE; reveal() }
            )
        } else if (picker == Picker.AUDIO) {
            TrackPickerPanel(
                player = player,
                picker = picker,
                onDismiss = { picker = Picker.NONE; reveal() }
            )
        }

        // Skip Intro button (bottom-end).
        if (showSkipIntro && picker == Picker.NONE) {
            androidx.tv.material3.Button(
                onClick = { player.seekTo(100_000L) },
                colors = androidx.tv.material3.ButtonDefaults.colors(
                    containerColor = Color(0xE6FFFFFF), contentColor = Color.Black
                ),
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 56.dp)
            ) {
                Text("Skip Intro", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
            }
        }

        // "Up Next" countdown card (bottom-end).
        if (showUpNext && countdown >= 0 && picker == Picker.NONE) {
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 40.dp, bottom = 40.dp)
                    .background(Color(0xF2141414), RoundedCornerShape(12.dp))
                    .padding(18.dp)
            ) {
                Text(
                    "Next episode in ${countdown}s",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Row(Modifier.padding(top = 10.dp)) {
                    androidx.tv.material3.Button(
                        onClick = { onNextEpisode?.invoke() },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color.White, contentColor = Color.Black
                        )
                    ) {
                        Text("Play now", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                    Box(Modifier.width(10.dp))
                    androidx.tv.material3.Button(
                        onClick = { upNextDismissed = true },
                        colors = androidx.tv.material3.ButtonDefaults.colors(
                            containerColor = Color(0x33FFFFFF), contentColor = Color.White
                        )
                    ) {
                        Text("Cancel", modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
        }

        // When hidden, a full-screen focusable catcher re-shows controls on any key.
        if (!visible && picker == Picker.NONE) {
            RevealCatcher(onReveal = { reveal() })
        }
    }
}

private enum class Picker { NONE, SUBTITLES, AUDIO }

@Composable
private fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.padding(horizontal = 4.dp)) {
        Icon(icon, contentDescription = desc, modifier = Modifier.size(30.dp))
    }
}

/** Small pill showing the current resolution (4K/1080p/…) next to the track buttons. */
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
private fun ProgressBar(position: Long, duration: Long) {
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            formatTime(position),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
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
        Text(
            formatTime(duration),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

@Composable
private fun FormatBadgeBar(badges: List<String>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .background(Color(0xCC000000), RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        badges.forEach { label ->
            Box(
                Modifier
                    .border(1.dp, Color.White, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrackPickerPanel(
    player: Player,
    picker: Picker,
    onDismiss: () -> Unit
) {
    val type = if (picker == Picker.SUBTITLES) C.TRACK_TYPE_TEXT else C.TRACK_TYPE_AUDIO
    val choices = remember(picker) { trackChoices(player, type) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x99000000)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.34f)
                .background(MaterialTheme.colorScheme.surface)
                .padding(24.dp)
        ) {
            Text(
                if (picker == Picker.SUBTITLES) "Subtitles" else "Audio",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picker == Picker.SUBTITLES) {
                    item {
                        TrackRow(label = "Off", selected = choices.none { it.selected }) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                .build()
                            onDismiss()
                        }
                    }
                }
                items(choices) { choice ->
                    TrackRow(label = choice.label, selected = choice.selected) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(type, false)
                            .setOverrideForType(TrackSelectionOverride(choice.group, choice.trackIndex))
                            .build()
                        onDismiss()
                    }
                }
                if (choices.isEmpty()) {
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

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (selected) "●  $label" else label,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/** Invisible focusable layer that re-shows controls when any key is pressed. */
@Composable
private fun RevealCatcher(onReveal: () -> Unit) {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .focusable()
            .onKeyEvent { ev ->
                // BACK must fall through so it exits the player (one level back).
                // Any other key re-reveals the controls.
                when {
                    ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_BACK -> false
                    ev.type == KeyEventType.KeyDown -> { onReveal(); true }
                    else -> false
                }
            }
    )
}

/**
 * The Nuvio-style subtitle panel for ExoPlayer: builds the Languages + Subtitles
 * lists from the player's text tracks (side-loaded SubSense tracks are matched
 * back by their Format.id so they show a "SubSense" badge + id), and hosts the
 * live subtitle-style controls.
 */
@Composable
private fun SubtitlesPanel(
    player: Player,
    sideloaded: List<com.streambert.tv.data.stream.SubtitleTrack>,
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    // Bumped after each selection so the checkmarks/highlights update live.
    var tick by remember { mutableStateOf(0) }
    val sideById = remember(sideloaded) {
        sideloaded.associateBy { it.id.ifBlank { it.url } }
    }
    val textTracks = remember(tick, player) { subtitleTextTracks(player) }
    val anySelected = textTracks.any { it.selected }

    val entries = textTracks.mapIndexed { idx, t ->
        val side = t.formatId?.let { sideById[it] }
        val source = if (side != null) side.source.ifBlank { "Add-on" } else "Built in"
        val language = displayLang(side?.language ?: t.langCode)
        val subId = side?.id ?: t.label.orEmpty()
        SubtitleEntry(
            key = "sub_${t.formatId ?: "e"}_$idx",
            source = source,
            language = language,
            subId = subId,
            selected = t.selected,
            onSelect = { selectTextTrack(player, t); tick++ }
        )
    }

    val languages = buildList {
        add(SubtitleLanguage(label = "None", count = 0, selected = !anySelected) {
            disableTextTracks(player); tick++
        })
        textTracks.groupBy { displayLang(it.langCode) }.forEach { (langName, tracks) ->
            add(
                SubtitleLanguage(
                    label = langName,
                    count = tracks.size,
                    selected = tracks.any { it.selected }
                ) { selectTextTrack(player, tracks.first()); tick++ }
            )
        }
    }

    SubtitleStylePanel(
        languages = languages,
        subtitles = entries,
        style = style,
        onStyleChange = onStyleChange,
        supportsDelay = false, // ExoPlayer has no subtitle-offset API (works on MPV)
        onDismiss = onDismiss
    )
}

// ── Track helpers ────────────────────────────────────────────────────────────

/** Compact snapshot of one text track for the subtitle panel. */
private data class TextTrackInfo(
    val group: TrackGroup,
    val index: Int,
    val formatId: String?,
    val langCode: String?,
    val label: String?,
    val selected: Boolean
)

private fun subtitleTextTracks(player: Player): List<TextTrackInfo> {
    val out = mutableListOf<TextTrackInfo>()
    for (group in player.currentTracks.groups) {
        if (group.type != C.TRACK_TYPE_TEXT) continue
        for (i in 0 until group.length) {
            val f = group.getTrackFormat(i)
            out.add(
                TextTrackInfo(
                    group = group.mediaTrackGroup,
                    index = i,
                    formatId = f.id,
                    langCode = f.language,
                    label = f.label,
                    selected = group.isTrackSelected(i)
                )
            )
        }
    }
    return out
}

private fun selectTextTrack(player: Player, t: TextTrackInfo) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(TrackSelectionOverride(t.group, t.index))
        .build()
}

private fun disableTextTracks(player: Player) {
    player.trackSelectionParameters = player.trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

private fun displayLang(code: String?): String {
    if (code.isNullOrBlank() || code == "und") return "Unknown"
    return runCatching { Locale(code).displayLanguage }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
}

private fun trackChoices(player: Player, trackType: Int): List<TrackChoice> {
    val out = mutableListOf<TrackChoice>()
    for (group in player.currentTracks.groups) {
        if (group.type != trackType) continue
        for (i in 0 until group.length) {
            val f = group.getTrackFormat(i)
            val lang = f.language?.let { code ->
                runCatching { Locale(code).displayLanguage }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
            }
            val label = f.label ?: lang ?: "Track ${i + 1}"
            out.add(TrackChoice(group.mediaTrackGroup, i, label, group.isTrackSelected(i)))
        }
    }
    return out
}
private fun detectResolution(tracks: Tracks): String? {
    var best = 0
    for (group in tracks.groups) {
        if (group.type != C.TRACK_TYPE_VIDEO) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val h = group.getTrackFormat(i).height
            if (h > best) best = h
        }
    }
    return when {
        best >= 2000 -> "4K"
        best >= 1400 -> "1440p"
        best >= 1000 -> "1080p"
        best >= 700 -> "720p"
        best >= 400 -> "480p"
        best > 0 -> "SD"
        else -> null
    }
}

private fun detectFormatBadges(tracks: Tracks): List<String> {
    val out = LinkedHashSet<String>()
    for (group in tracks.groups) {
        for (i in 0 until group.length) {
            if (!group.isTrackSelected(i)) continue
            val f = group.getTrackFormat(i)
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    val codecs = f.codecs.orEmpty()
                    if (f.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION ||
                        codecs.startsWith("dvh") || codecs.startsWith("dva")
                    ) out.add("DOLBY VISION")
                    when (f.colorInfo?.colorTransfer) {
                        C.COLOR_TRANSFER_ST2084 -> out.add("HDR10")
                        C.COLOR_TRANSFER_HLG -> out.add("HLG")
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    when (f.sampleMimeType) {
                        MimeTypes.AUDIO_E_AC3_JOC -> out.add("DOLBY ATMOS")
                        MimeTypes.AUDIO_AC4 -> out.add("DOLBY AC-4")
                        MimeTypes.AUDIO_TRUEHD -> out.add("DOLBY TRUEHD")
                        MimeTypes.AUDIO_E_AC3 -> out.add("DOLBY DIGITAL+")
                        MimeTypes.AUDIO_AC3 -> out.add("DOLBY DIGITAL")
                        MimeTypes.AUDIO_DTS_X -> out.add("DTS:X")
                        MimeTypes.AUDIO_DTS_HD -> out.add("DTS-HD")
                        MimeTypes.AUDIO_DTS -> out.add("DTS")
                    }
                }
            }
        }
    }
    return out.toList()
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}
