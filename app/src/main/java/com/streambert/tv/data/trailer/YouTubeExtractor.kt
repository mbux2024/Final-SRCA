package com.streambert.tv.data.trailer

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** A resolved, directly-playable trailer stream. */
data class TrailerStream(
    val videoUrl: String,
    val audioUrl: String? = null
)

/**
 * Resolves a YouTube video id into a **direct** stream URL that a native player
 * (MPV/ExoPlayer) can play — no WebView / IFrame player required. This is the
 * robust approach for Android TV, where the YouTube IFrame player often refuses
 * to play.
 *
 * Like NuvioTV, it calls YouTube's public InnerTube `player` endpoint and tries
 * several clients (ANDROID_VR → ANDROID → iOS), which return un-ciphered,
 * un-throttled progressive/adaptive formats. Our own implementation against the
 * public InnerTube API.
 */
class YouTubeExtractor {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /** Try each candidate video id (in priority order) until one resolves. */
    suspend fun extractFirst(videoIds: List<String>): TrailerStream? {
        for (id in videoIds) {
            val s = extract(id)
            if (s != null) return s
        }
        return null
    }

    /** Resolve the best playable stream for [videoId] across all clients, or null. */
    suspend fun extract(videoId: String): TrailerStream? = withContext(Dispatchers.IO) {
        val id = videoId.trim()
        if (!id.matches(VIDEO_ID_REGEX)) return@withContext null

        for (yc in CLIENTS) {
            val root = runCatching { requestPlayer(id, yc) }.getOrNull() ?: continue
            val status = root["playabilityStatus"]?.jsonObject
                ?.get("status")?.jsonPrimitive?.contentOrNull
            if (status != null && !status.equals("OK", ignoreCase = true)) {
                Log.w(TAG, "client=${yc.name} playabilityStatus=$status for $id")
                continue
            }
            val stream = parseStream(root) ?: continue
            Log.d(TAG, "Resolved $id via client=${yc.name}")
            return@withContext stream
        }
        Log.w(TAG, "All clients failed for $id")
        null
    }

    private fun parseStream(root: JsonObject): TrailerStream? {
        val streaming = root["streamingData"]?.jsonObject ?: return null
        val progressive = (streaming["formats"] as? JsonArray).orEmpty()
        val adaptive = (streaming["adaptiveFormats"] as? JsonArray).orEmpty()

        // Prefer a progressive muxed format (single URL with audio + video).
        val muxed = progressive
            .mapNotNull { it.jsonObject.toFormat() }
            .filter { it.url != null }
            .maxByOrNull { it.height }
        if (muxed?.url != null) {
            Log.d(TAG, "Using progressive muxed itag=${muxed.itag} ${muxed.height}p")
            return TrailerStream(videoUrl = muxed.url)
        }

        // Otherwise combine best adaptive video-only + best audio-only.
        val formats = adaptive.mapNotNull { it.jsonObject.toFormat() }.filter { it.url != null }
        val bestVideo = formats
            .filter { it.mimeType?.startsWith("video/") == true }
            .maxByOrNull { it.height }
        val bestAudio = formats
            .filter { it.mimeType?.startsWith("audio/") == true }
            .maxByOrNull { it.bitrate }
        if (bestVideo?.url != null) {
            Log.d(TAG, "Using adaptive video ${bestVideo.height}p + audio=${bestAudio?.url != null}")
            return TrailerStream(videoUrl = bestVideo.url, audioUrl = bestAudio?.url)
        }
        return null
    }

    private fun requestPlayer(videoId: String, yc: YtClient): JsonObject? {
        val body = """
            {
              "videoId": "$videoId",
              "contentCheckOk": true,
              "racyCheckOk": true,
              "context": { "client": ${yc.contextJson} }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$INNERTUBE_KEY")
            .addHeader("Content-Type", "application/json")
            .addHeader("User-Agent", yc.userAgent)
            .addHeader("X-YouTube-Client-Name", yc.clientNameId)
            .addHeader("X-YouTube-Client-Version", yc.clientVersion)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "client=${yc.name} InnerTube HTTP ${resp.code}")
                return null
            }
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return null
            return json.parseToJsonElement(text).jsonObject
        }
    }

    private fun JsonObject.toFormat(): Fmt? {
        // Skip ciphered formats (they need JS deciphering we don't do). These
        // clients normally return a plain `url`.
        val url = this["url"]?.jsonPrimitive?.contentOrNull ?: return null
        val itag = this["itag"]?.jsonPrimitive?.intOrNull ?: 0
        val mime = this["mimeType"]?.jsonPrimitive?.contentOrNull
        val height = this["height"]?.jsonPrimitive?.intOrNull ?: 0
        val bitrate = this["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
        return Fmt(itag, url, mime, height, bitrate)
    }

    private data class Fmt(
        val itag: Int,
        val url: String?,
        val mimeType: String?,
        val height: Int,
        val bitrate: Int
    )

    private data class YtClient(
        val name: String,
        val clientNameId: String,
        val clientVersion: String,
        val userAgent: String,
        val contextJson: String
    )

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    companion object {
        private const val TAG = "YouTubeExtractor"
        // Public InnerTube key (widely documented; also NuvioTV's fallback).
        private const val INNERTUBE_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")

        private val CLIENTS = listOf(
            YtClient(
                name = "ANDROID_VR",
                clientNameId = "28",
                clientVersion = "1.56.21",
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.56.21 " +
                    "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1) gzip",
                contextJson = """
                    {"clientName":"ANDROID_VR","clientVersion":"1.56.21","deviceMake":"Oculus",
                     "deviceModel":"Quest 3","osName":"Android","osVersion":"12",
                     "androidSdkVersion":32,"hl":"en","gl":"US"}
                """.trimIndent()
            ),
            YtClient(
                name = "ANDROID",
                clientNameId = "3",
                clientVersion = "20.10.35",
                userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
                contextJson = """
                    {"clientName":"ANDROID","clientVersion":"20.10.35","osName":"Android",
                     "osVersion":"14","androidSdkVersion":34,"hl":"en","gl":"US"}
                """.trimIndent()
            ),
            YtClient(
                name = "IOS",
                clientNameId = "5",
                clientVersion = "20.10.1",
                userAgent = "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)",
                contextJson = """
                    {"clientName":"IOS","clientVersion":"20.10.1","deviceModel":"iPhone16,2",
                     "osName":"iPhone","osVersion":"17.4.0.21E219","hl":"en","gl":"US"}
                """.trimIndent()
            )
        )
    }
}
