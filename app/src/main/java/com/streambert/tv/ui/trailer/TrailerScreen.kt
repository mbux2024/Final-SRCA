package com.streambert.tv.ui.trailer

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * In-app trailer player. Resolves the YouTube id (from TMDB) to a direct stream
 * via [TrailerViewModel] and plays it in **ExoPlayer** (trailers are simple
 * progressive/adaptive streams). We deliberately do NOT use the MPV engine here —
 * libmpv is a single native context shared with the main player, and spinning up
 * a second instance for the trailer caused crashes on exit.
 */
@OptIn(UnstableApi::class)
@Composable
fun TrailerScreen(viewModel: TrailerViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is TrailerUiState.Loading -> {
                Text(
                    "Loading trailer…",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is TrailerUiState.Error -> {
                Text(
                    "Trailer unavailable.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            is TrailerUiState.Ready -> {
                val context = LocalContext.current
                val exo = remember(s.stream.videoUrl) {
                    ExoPlayer.Builder(context).build().apply {
                        val dsf = DefaultDataSource.Factory(context)
                        val videoUrl = s.stream.videoUrl
                        val audioUrl = s.stream.audioUrl
                        val source = if (audioUrl.isNullOrBlank()) {
                            ProgressiveMediaSource.Factory(dsf)
                                .createMediaSource(MediaItem.fromUri(videoUrl))
                        } else {
                            // Adaptive: merge separate video + audio tracks.
                            MergingMediaSource(
                                ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(videoUrl)),
                                ProgressiveMediaSource.Factory(dsf).createMediaSource(MediaItem.fromUri(audioUrl))
                            )
                        }
                        setMediaSource(source)
                        prepare()
                        playWhenReady = true
                    }
                }
                DisposableEffect(s.stream.videoUrl) {
                    onDispose { exo.release() }
                }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exo
                            useController = false
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    }
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(28.dp),
                tint = Color.White
            )
        }
    }
}
