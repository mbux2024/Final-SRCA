package com.streambert.tv.ui.player

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import `is`.xyz.mpv.BaseMPVView
import `is`.xyz.mpv.Utils
import java.util.Locale
import kotlin.math.roundToLong

/**
 * A thin [BaseMPVView] wrapper that drives libmpv (via the mpv-android-lib
 * artifact). MPV decodes far more containers/codecs than ExoPlayer — HEVC 10-bit,
 * Dolby Vision, AV1, DTS/DTS-HD/TrueHD, ASS/SSA subtitles — which is why it plays
 * the releases that black-screen on ExoPlayer.
 *
 * This is our own code written against the public `is.xyz.mpv` API; it does not
 * reuse any GPL-licensed player source.
 */
class MpvPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : BaseMPVView(context, attrs) {

    private var initialized = false
    private var loadedUrl: String? = null

    /**
     * hwdec value applied on init. "auto-safe" lets mpv pick the zero-copy hardware
     * decoder that's known-safe (mediacodec direct on Android TV) — this is what
     * NuvioTV uses and is dramatically smoother for 4K / HDR / Dolby Vision than
     * "mediacodec-copy" (which copies every frame back to the CPU).
     */
    var hardwareDecodeMode: String = "auto-safe"

    fun ensureInitialized() {
        if (initialized) return
        Utils.copyAssets(context)
        initialize(configDir = context.filesDir.path, cacheDir = context.cacheDir.path)
        initialized = true
    }

    /** Load a URL and begin playback, optionally seeking to [startPositionMs]. */
    fun setMedia(url: String, startPositionMs: Long = 0L, audioUrl: String? = null) {
        ensureInitialized()
        if (url == loadedUrl) return
        loadedUrl = url
        val startOption = startPositionMs.takeIf { it > 0L }
            ?.let { String.format(Locale.US, "start=%.3f", it / 1000.0) }
        runCatching {
            if (startOption != null) {
                mpv.command("loadfile", url, "replace", startOption)
            } else {
                mpv.command("loadfile", url, "replace")
            }
            // Adaptive trailers come as separate video + audio streams.
            if (!audioUrl.isNullOrBlank()) {
                mpv.command("audio-add", audioUrl, "select")
            }
            mpv.setPropertyString("aid", "auto")
            mpv.setPropertyString("sid", "auto")
            mpv.setPropertyBoolean("sub-visibility", true)
        }.onFailure { Log.w(TAG, "loadfile failed: ${it.message}") }
    }

    fun setPaused(paused: Boolean) {
        if (!initialized) return
        runCatching { mpv.setPropertyBoolean("pause", paused) }
    }

    fun isPlayingNow(): Boolean {
        if (!initialized) return false
        return runCatching { mpv.getPropertyBoolean("pause") == false }.getOrDefault(false)
    }

    fun seekToMs(positionMs: Long) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("time-pos", positionMs.coerceAtLeast(0L) / 1000.0) }
    }

    fun currentPositionMs(): Long {
        if (!initialized) return 0L
        val seconds = runCatching { mpv.getPropertyDouble("time-pos") }.getOrNull() ?: 0.0
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun durationMs(): Long {
        if (!initialized) return 0L
        val seconds = runCatching { mpv.getPropertyDouble("duration") }.getOrNull() ?: 0.0
        return (seconds * 1000.0).roundToLong().coerceAtLeast(0L)
    }

    fun setPlaybackSpeed(speed: Float) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("speed", speed.toDouble()) }
    }

    /** Zoom/crop the video to fill the screen (panscan 0 = fit, 1 = fill/crop). */
    fun setPanscan(value: Double) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("panscan", value.coerceIn(0.0, 1.0)) }
    }

    /** Nudge subtitle timing (seconds; +later / -earlier). */
    fun setSubtitleDelay(seconds: Double) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("sub-delay", seconds) }
    }

    /** Subtitle text scale, where 1.0 is mpv's default size. */
    fun applySubtitleScale(scale: Float) {
        if (!initialized) return
        runCatching { mpv.setPropertyDouble("sub-scale", scale.coerceIn(0.3f, 4.0f).toDouble()) }
    }

    /** Bold subtitle text. */
    fun setSubtitleBold(bold: Boolean) {
        if (!initialized) return
        runCatching { mpv.setPropertyBoolean("sub-bold", bold) }
    }

    /** Subtitle text color from an ARGB int (alpha ignored; mpv uses opaque #RRGGBB). */
    fun setSubtitleColor(argb: Int) {
        if (!initialized) return
        val hex = String.format(Locale.US, "#%06X", argb and 0x00FFFFFF)
        runCatching { mpv.setPropertyString("sub-color", hex) }
    }

    /** Toggle the subtitle outline (border). */
    fun setSubtitleOutline(enabled: Boolean) {
        if (!initialized) return
        runCatching {
            mpv.setPropertyDouble("sub-border-size", if (enabled) 3.0 else 0.0)
            mpv.setPropertyString("sub-border-color", "#000000")
        }
    }

    /**
     * Apply preferred audio/subtitle languages ("none" = leave default). MPV uses
     * `alang`/`slang` to auto-pick matching tracks; setting slang also turns
     * subtitles on for that language.
     */
    fun setPreferredLanguages(audio: String?, subtitle: String?) {
        if (!initialized) return
        runCatching {
            audio?.takeIf { it.isNotBlank() && !it.equals("none", true) }?.let {
                mpv.setPropertyString("alang", languageAliases(it))
            }
            subtitle?.takeIf { it.isNotBlank() && !it.equals("none", true) }?.let {
                mpv.setPropertyString("slang", languageAliases(it))
                mpv.setPropertyBoolean("sub-visibility", true)
            }
        }
    }

    /** Expand a 2-letter code to also include common 3-letter tags mpv may see. */
    private fun languageAliases(code: String): String {
        val c = code.lowercase(Locale.ROOT)
        val three = TWO_TO_THREE[c]
        return if (three != null) "$c,$three" else c
    }

    fun selectAudioTrack(id: Int) {        if (!initialized) return
        runCatching { mpv.setPropertyInt("aid", id) }
    }

    fun selectSubtitleTrack(id: Int) {
        if (!initialized) return
        runCatching {
            mpv.setPropertyBoolean("sub-visibility", true)
            mpv.setPropertyInt("sid", id)
        }
    }

    fun disableSubtitles() {
        if (!initialized) return
        runCatching {
            mpv.setPropertyString("sid", "no")
            mpv.setPropertyBoolean("sub-visibility", false)
        }
    }

    /** Side-load an external subtitle (e.g. from OpenSubtitles) without auto-selecting. */
    fun addExternalSubtitle(url: String, title: String?, language: String?) {
        if (!initialized || url.isBlank()) return
        runCatching {
            val t = title?.takeIf { it.isNotBlank() }
            val l = language?.takeIf { it.isNotBlank() }
            when {
                t != null && l != null -> mpv.command("sub-add", url, "auto", t, l)
                t != null -> mpv.command("sub-add", url, "auto", t)
                else -> mpv.command("sub-add", url, "auto")
            }
        }.onFailure { Log.w(TAG, "sub-add failed: ${it.message}") }
    }

    /** Snapshot the current audio + subtitle tracks for the picker UI. */
    fun readTracks(): List<MpvTrack> {
        if (!initialized) return emptyList()
        val count = runCatching { mpv.getPropertyInt("track-list/count") }.getOrNull() ?: return emptyList()
        if (count <= 0) return emptyList()
        val selectedAid = runCatching { mpv.getPropertyString("aid")?.toIntOrNull() }.getOrNull()
        val selectedSid = runCatching { mpv.getPropertyString("sid")?.toIntOrNull() }.getOrNull()
        val out = ArrayList<MpvTrack>(count)
        for (i in 0 until count) {
            val type = runCatching { mpv.getPropertyString("track-list/$i/type") }.getOrNull()?.lowercase() ?: continue
            if (type != "audio" && type != "sub") continue
            val id = runCatching { mpv.getPropertyInt("track-list/$i/id") }.getOrNull() ?: continue
            val lang = runCatching { mpv.getPropertyString("track-list/$i/lang") }.getOrNull()?.trim()?.ifBlank { null }
            val trackTitle = runCatching { mpv.getPropertyString("track-list/$i/title") }.getOrNull()?.trim()?.ifBlank { null }
            val codec = runCatching { mpv.getPropertyString("track-list/$i/codec") }.getOrNull()?.trim()?.ifBlank { null }
            val selectedFlag = runCatching { mpv.getPropertyBoolean("track-list/$i/selected") }.getOrNull() == true
            val external = runCatching { mpv.getPropertyBoolean("track-list/$i/external") }.getOrNull() == true
            val selected = selectedFlag ||
                (type == "audio" && selectedAid != null && selectedAid == id) ||
                (type == "sub" && selectedSid != null && selectedSid == id)
            out.add(
                MpvTrack(
                    id = id,
                    type = type,
                    label = trackTitle ?: lang ?: "${type.replaceFirstChar { it.uppercase() }} $id",
                    codec = codec,
                    selected = selected,
                    lang = lang,
                    external = external
                )
            )
        }
        return out
    }
    /**
     * Detect the playing resolution + premium formats from mpv's live properties.
     * Atmos / DTS:X aren't exposed as flags by mpv, so we also scan the selected
     * track's title (release names usually say "Atmos" / "DTS-X").
     */
    fun mediaBadges(): MediaBadges {
        if (!initialized) return MediaBadges(null, emptyList())
        val h = runCatching { mpv.getPropertyInt("video-params/h") }.getOrNull()
            ?: runCatching { mpv.getPropertyInt("height") }.getOrNull() ?: 0
        val resolution = resolutionLabel(h)
        val out = LinkedHashSet<String>()
        resolution?.let { out.add(it) }

        val vCodec = strProp("current-tracks/video/codec")
        val vTitle = strProp("current-tracks/video/title")
        val gamma = strProp("video-params/gamma")
        if (vCodec.contains("dvhe") || vCodec.contains("dvh1") || vCodec.contains("dva") ||
            vTitle.contains("dolby vision") || vTitle.contains("dovi") || Regex("\\bdv\\b").containsMatchIn(vTitle)
        ) out.add("DOLBY VISION")
        when {
            vTitle.contains("hdr10+") || vTitle.contains("hdr10plus") -> out.add("HDR10+")
            gamma.contains("pq") || gamma.contains("st2084") -> out.add("HDR10")
            gamma.contains("hlg") -> out.add("HLG")
        }

        val aCodec = strProp("current-tracks/audio/codec")
        val aTitle = strProp("current-tracks/audio/title")
        when {
            aTitle.contains("atmos") -> out.add("DOLBY ATMOS")
            aCodec.contains("truehd") -> out.add("DOLBY TRUEHD")
            aTitle.contains("dts:x") || aTitle.contains("dts-x") || aTitle.contains("dtsx") -> out.add("DTS:X")
            aCodec.contains("dts") -> if (aTitle.contains("dts-hd") || aTitle.contains("dts hd")) out.add("DTS-HD") else out.add("DTS")
            aCodec.contains("eac3") -> out.add("DOLBY DIGITAL+")
            aCodec.contains("ac3") -> out.add("DOLBY DIGITAL")
        }
        return MediaBadges(resolution, out.toList())
    }

    private fun strProp(name: String): String =
        runCatching { mpv.getPropertyString(name) }.getOrNull()?.lowercase(Locale.ROOT).orEmpty()

    private fun resolutionLabel(height: Int): String? = when {
        height >= 2000 -> "4K"
        height >= 1400 -> "1440p"
        height >= 1000 -> "1080p"
        height >= 700 -> "720p"
        height >= 400 -> "480p"
        height > 0 -> "SD"
        else -> null
    }

    fun releasePlayer() {
        if (!initialized) return
        runCatching { destroy() }.onFailure { Log.w(TAG, "destroy failed: ${it.message}") }
        initialized = false
        loadedUrl = null
    }

    override fun initOptions() {
        // "fast" profile favours smooth playback on TV hardware (cheap bilinear
        // scalers, no debanding/dithering/HDR-peak-detection).
        mpv.setOptionString("profile", "fast")
        setVo("gpu")
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")

        // --- Hardware decode (zero-copy path on TV silicon) ---
        mpv.setOptionString("hwdec", hardwareDecodeMode)
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")

        // --- CPU relief for the software-decode fallback ---
        // When mediacodec can't take a codec (some HEVC-10/DV/DTS combos), mpv
        // falls back to the CPU. These make that fallback far lighter and are the
        // difference between smooth and stuttering on weaker boxes:
        //  - threads=0  -> use every core for decoding
        //  - skiploopfilter=nonref -> skip the deblocking filter on non-reference
        //    frames (large CPU win, negligible visible quality loss)
        //  - framedrop=vo -> let the renderer drop late frames instead of building
        //    up A/V desync and audio underruns.
        mpv.setOptionString("vd-lavc-threads", "0")
        mpv.setOptionString("vd-lavc-fast", "yes")
        mpv.setOptionString("vd-lavc-skiploopfilter", "nonref")
        mpv.setOptionString("framedrop", "vo")
        mpv.setOptionString("video-sync", "audio")
        mpv.setOptionString("interpolation", "no")
        mpv.setOptionString("video-latency-hacks", "yes")

        // Android audio out; audiotrack does compressed passthrough (AC3/EAC3/DTS)
        // to the AVR/TV where supported, offloading Atmos/DTS decoding.
        mpv.setOptionString("ao", "audiotrack,opensles")
        mpv.setOptionString("audio-set-media-role", "yes")
        mpv.setOptionString("user-agent", "StreambertTV")
        // Keep native ASS/SSA styling.
        mpv.setOptionString("sub-ass-override", "no")
        mpv.setOptionString("sub-font", "Roboto")
        mpv.setOptionString("sub-use-margins", "yes")
        mpv.setOptionString("sub-ass-force-margins", "yes")
        mpv.setOptionString("input-default-bindings", "yes")
        mpv.setOptionString("softvol", "yes")
        mpv.setOptionString("hr-seek", "yes")
        // TLS: use the CA bundle copied by Utils.copyAssets.
        mpv.setOptionString("tls-verify", "yes")
        mpv.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")

        // --- Network buffering (prevents rebuffer stutter on high-bitrate remuxes) ---
        // A read-ahead cache keeps playback fed when the debrid/CDN link hiccups.
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("cache-secs", "30")
        mpv.setOptionString("demuxer-readahead-secs", "20")
        mpv.setOptionString("demuxer-max-bytes", "${64 * 1024 * 1024}")
        mpv.setOptionString("demuxer-max-back-bytes", "${32 * 1024 * 1024}")
        mpv.setOptionString("keep-open", "yes")
    }

    override fun postInitOptions() {
        mpv.setOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        // Position/duration are polled by the composable; nothing to observe here.
    }

    companion object {
        private const val TAG = "MpvPlayerView"
        private val TWO_TO_THREE = mapOf(
            "en" to "eng", "es" to "spa", "fr" to "fre", "de" to "ger", "it" to "ita",
            "pt" to "por", "ru" to "rus", "ja" to "jpn", "ko" to "kor", "zh" to "chi",
            "ar" to "ara", "hi" to "hin", "nl" to "dut", "pl" to "pol", "tr" to "tur",
            "sv" to "swe", "no" to "nor", "da" to "dan", "fi" to "fin", "cs" to "cze"
        )
    }
}

/** A libmpv audio or subtitle track for the picker UI. */
data class MpvTrack(
    val id: Int,
    val type: String, // "audio" | "sub"
    val label: String,
    val codec: String?,
    val selected: Boolean,
    val lang: String? = null,
    val external: Boolean = false
)

/** Playing resolution label + premium-format badges (HDR/DV/Atmos/DTS…). */
data class MediaBadges(
    val resolution: String?,
    val badges: List<String>
)
