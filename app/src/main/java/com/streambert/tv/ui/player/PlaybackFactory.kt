package com.streambert.tv.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.streambert.tv.data.stream.SubtitleTrack
import okhttp3.OkHttpClient

/**
 * Builds an [ExoPlayer] tuned for premium home-theatre playback:
 *
 *  • **Dolby Vision / HDR10 / HDR10+ / HLG** — handled by the device's video
 *    decoder + display. We keep the default (hardware) MediaCodec renderers and
 *    enable *tunneled* playback, which is the recommended path for 4K/HDR/DV on
 *    Android TV & Google TV and gives the decoder/display the HDR metadata.
 *
 *  • **Dolby Atmos (E-AC3 JOC) / Dolby Digital / DTS / DTS-HD / DTS:X / TrueHD**
 *    — passed through (bit-streamed) untouched to a connected AVR/soundbar when
 *    the HDMI/eARC sink reports support for those encodings, instead of being
 *    downmixed to PCM stereo. ExoPlayer's DefaultAudioSink does this
 *    automatically based on the device AudioCapabilities; we just make sure we
 *    don't get in the way and we prefer decoder extensions when present.
 *
 * Passthrough of Atmos/DTS:X ultimately depends on the hardware chain
 * (device → HDMI/eARC → receiver). On devices without a passthrough-capable
 * sink, core Media3 can decode Dolby (AC3/E-AC3) but **DTS decoding needs the
 * optional media3 FFmpeg decoder extension** (built from source) — see README.
 */
object PlaybackFactory {

    // Shared OkHttp client — reuses TCP/TLS connections across player instances.
    // NuvioTV uses a similar shared pool (8 connections, HTTP/2).
    private val sharedHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(5, 3, java.util.concurrent.TimeUnit.MINUTES))
        .build()

    @UnstableApi
    fun create(
        context: Context,
        startPositionMs: Long,
        mediaUri: String,
        tunnelingEnabled: Boolean = false,
        subtitles: List<SubtitleTrack> = emptyList()
    ): ExoPlayer {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            // Fall back to another decoder if the primary one fails to init
            // (common with exotic HDR/DV or high-bitrate streams).
            setEnableDecoderFallback(true)
            // Use bundled decoder extensions (e.g. FFmpeg for DTS) when they are
            // on the classpath; harmless no-op otherwise.
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        }

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    // Tunneled playback gives the best A/V sync + HDR/DV handoff on
                    // capable TVs, but many devices misreport support and render a
                    // BLACK SCREEN with audio only. So it is OFF by default and can
                    // be turned on per-device from Settings.
                    .setTunnelingEnabled(tunnelingEnabled)
            )
        }

        // Start playback sooner: smaller "buffer for playback" thresholds so we
        // begin rendering as soon as a little data is available instead of
        // waiting for a large buffer (helps big 4K remuxes over the network).
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Shared OkHttp client with connection pooling (reuses TCP/TLS connections
        // across player instances for faster startup on subsequent plays).
        val httpDataSourceFactory = OkHttpDataSource.Factory(sharedHttpClient)
            .setUserAgent("StreambertTV")

        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                // handleAudioFocus = true so we duck/pause correctly.
                setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)

                val subtitleConfigs = subtitles.map { sub ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(sub.url))
                        .setMimeType(sub.mimeType)
                        .setLanguage(sub.language)
                        .setLabel(sub.displayLanguage)
                        // Carry the provider id onto the resulting Format.id so the
                        // subtitle panel can label it (source + id), Nuvio-style.
                        .setId(sub.id.ifBlank { sub.url })
                        .build()
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setSubtitleConfigurations(subtitleConfigs)
                    .build()
                setMediaItem(mediaItem)
                if (startPositionMs > 0) seekTo(startPositionMs)
                playWhenReady = true
                prepare()
            }
    }
}
