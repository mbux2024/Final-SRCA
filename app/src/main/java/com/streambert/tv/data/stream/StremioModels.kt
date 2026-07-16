package com.streambert.tv.data.stream

import kotlinx.serialization.Serializable

/**
 * Stremio addon stream response. Both Torrentio and Comet implement the
 * Stremio addon protocol, returning a `streams` array. When a debrid service
 * (TorBox) is configured, each entry carries a directly playable `url`.
 *
 * Reference: Stremio addon "stream" resource.
 */
@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream> = emptyList()
)

@Serializable
data class StremioStream(
    // Short label, e.g. "Torrentio\n1080p" or "[TorBox+] 4K"
    val name: String? = null,
    // Full release title (filename / details)
    val title: String? = null,
    val description: String? = null,
    // Direct playable URL (present once a debrid service resolves the stream)
    val url: String? = null,
    // Present only for un-resolved raw torrents
    val infoHash: String? = null,
    val fileIdx: Int? = null
) {
    /** Best available human label across the differently-named fields. */
    val label: String get() = (title ?: name ?: description ?: "Stream").replace("\n", " ")
}

/** Stremio subtitles response (e.g. OpenSubtitles addon). */
@Serializable
data class StremioSubtitlesResponse(
    val subtitles: List<StremioSubtitle> = emptyList()
)

@Serializable
data class StremioSubtitle(
    val id: String? = null,
    val url: String? = null,
    val lang: String? = null,
    @kotlinx.serialization.SerialName("SubEncoding") val encoding: String? = null
)
