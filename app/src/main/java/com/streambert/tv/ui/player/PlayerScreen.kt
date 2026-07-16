package com.streambert.tv.ui.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.streambert.tv.ui.components.LoadingIndicator
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (val s = state) {
            is PlayerUiState.Resolving -> ResolvingView(s.message)
            is PlayerUiState.Error -> ErrorView(s.message, onRetry = { viewModel.resolve() }, onBack = onBack)
            is PlayerUiState.Ready -> {
                // Only offer next-episode (button + autoplay) when one actually exists.
                val effectiveNext = if (s.hasNextEpisode) onNextEpisode else null
                if (s.playerEngine == com.streambert.tv.data.settings.SettingsRepository.ENGINE_EXOPLAYER) {
                    VideoPlayer(
                        url = s.url,
                        title = s.title,
                        startPositionMs = s.startPositionMs,
                        tunnelingEnabled = s.tunnelingEnabled,
                        subtitles = s.subtitles,
                        subtitleScale = s.subtitleScale,
                        subtitleStyle = s.subtitleStyle,
                        onSubtitleStyleChange = viewModel::updateSubtitleStyle,
                        autoplayNext = s.prefs.autoplayNextEnabled,
                        skipIntro = s.prefs.skipIntroEnabled && s.prefs.isSeries,
                        onSaveProgress = viewModel::saveProgress,
                        onError = viewModel::onPlaybackError,
                        onDecoderUnsupported = viewModel::onDecoderUnsupported,
                        onBack = onBack,
                        onNextEpisode = effectiveNext
                    )
                } else {
                    MpvVideoPlayer(
                        url = s.url,
                        title = s.title,
                        startPositionMs = s.startPositionMs,
                        subtitles = s.subtitles,
                        subtitleScale = s.subtitleScale,
                        subtitleStyle = s.subtitleStyle,
                        onSubtitleStyleChange = viewModel::updateSubtitleStyle,
                        prefs = s.prefs,
                        onSaveProgress = viewModel::saveProgress,
                        onBack = onBack,
                        onNextEpisode = effectiveNext
                    )
                }
            }
        }
    }
}

@Composable
private fun ResolvingView(message: String) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LoadingIndicator()
        Text(
            message,
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 20.dp)
        )
        Text(
            "Powered by TorBox",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Playback error",
            color = Color.White,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            message,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onRetry) {
                Text("Retry", modifier = Modifier.padding(horizontal = 12.dp))
            }
            Button(onClick = onBack) {
                Text("Back", modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoPlayer(
    url: String,
    title: String,
    startPositionMs: Long,
    tunnelingEnabled: Boolean,
    subtitles: List<com.streambert.tv.data.stream.SubtitleTrack>,
    subtitleScale: Float,
    subtitleStyle: SubtitleStyle,
    onSubtitleStyleChange: (SubtitleStyle) -> Unit,
    autoplayNext: Boolean,
    skipIntro: Boolean,
    onSaveProgress: (positionMs: Long, durationMs: Long) -> Unit,
    onError: (String) -> Unit,
    onDecoderUnsupported: (String) -> Unit,
    onBack: () -> Unit,
    onNextEpisode: (() -> Unit)?
) {
    val context = LocalContext.current
    // Live subtitle style: applied to the SubtitleView and persisted upward.
    var style by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(subtitleStyle) }
    var playerViewRef by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<PlayerView?>(null)
    }
    LaunchedEffect(style, playerViewRef, subtitleScale) {
        playerViewRef?.subtitleView?.apply {
            setFractionalTextSize(subtitleScale * style.fontPercent / 100f)
            setStyle(captionStyleFrom(style))
        }
    }

    val exoPlayer = remember(url) {
        PlaybackFactory.create(
            context = context,
            startPositionMs = startPositionMs,
            mediaUri = url,
            tunnelingEnabled = tunnelingEnabled,
            subtitles = subtitles
        )
    }

    // Report fatal decoder/network errors up to the ViewModel so the user sees a
    // message + Retry instead of a silent black screen.
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                val msg = error.localizedMessage ?: error.errorCodeName
                // A decoder-capability/init failure (e.g. this device can't decode
                // 4K HEVC or Dolby Vision) → hand off to the MPV software engine
                // instead of dead-ending on an error screen.
                val decoderIssue =
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    (error.message?.contains("NO_EXCEEDS_CAPABILITIES") == true) ||
                    (error.message?.contains("MediaCodecVideoRenderer") == true)
                if (decoderIssue) onDecoderUnsupported(msg) else onError(msg)
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Persist progress periodically while playing.
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(10_000)
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0 && pos > 0) onSaveProgress(pos, dur)
        }
    }

    // Save the final position when leaving the player.
    DisposableEffect(url) {
        onDispose {
            val dur = exoPlayer.duration
            val pos = exoPlayer.currentPosition
            if (dur > 0 && pos > 0) onSaveProgress(pos, dur)
            exoPlayer.release()
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false   // we draw our own Netflix-style controls
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    // Style is applied live via the LaunchedEffect above.
                    playerViewRef = this
                }
            }
        )
        PlayerControls(
            player = exoPlayer,
            title = title,
            sideloaded = subtitles,
            subtitleStyle = style,
            onSubtitleStyleChange = { style = it; onSubtitleStyleChange(it) },
            autoplayNext = autoplayNext,
            skipIntro = skipIntro,
            onBack = onBack,
            onNextEpisode = onNextEpisode
        )
    }
}


/** Builds a Media3 caption style from the user's subtitle-style choices. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun captionStyleFrom(s: SubtitleStyle): androidx.media3.ui.CaptionStyleCompat {
    val alpha = s.opacityPercent.coerceIn(0, 100) * 255 / 100
    val fg = (s.textColor and 0x00FFFFFF) or (alpha shl 24)
    val edge = if (s.outline) androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
    else androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
    val tf = if (s.bold) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
    return androidx.media3.ui.CaptionStyleCompat(
        fg,
        android.graphics.Color.TRANSPARENT,
        android.graphics.Color.TRANSPARENT,
        edge,
        android.graphics.Color.BLACK,
        tf
    )
}
