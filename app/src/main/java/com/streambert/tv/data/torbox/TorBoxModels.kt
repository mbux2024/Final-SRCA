package com.streambert.tv.data.torbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic TorBox envelope. Most endpoints return:
 *   { "success": true, "error": null, "detail": "...", "data": <payload> }
 *
 * NOTE: TorBox occasionally returns `data` as an object, an array, or a bare
 * string (e.g. the download link). Endpoints below pick the concrete payload
 * type per call. Field names verified against the public docs at
 * https://api-docs.torbox.app — adjust there if TorBox changes its schema.
 */
@Serializable
data class TorBoxEnvelope<T>(
    val success: Boolean = false,
    val error: String? = null,
    val detail: String? = null,
    val data: T? = null
)

// ── Main API (api.torbox.app) ─────────────────────────────────────────────────

/** Returned by POST /v1/api/torrents/createtorrent */
@Serializable
data class CreateTorrentData(
    @SerialName("torrent_id") val torrentId: Long? = null,
    @SerialName("queued_id") val queuedId: Long? = null,
    val hash: String? = null,
    val auth_id: String? = null
)

/** A single torrent entry from GET /v1/api/torrents/mylist */
@Serializable
data class TorBoxTorrent(
    val id: Long = 0,
    val hash: String? = null,
    val name: String? = null,
    @SerialName("download_finished") val downloadFinished: Boolean = false,
    @SerialName("download_present") val downloadPresent: Boolean = false,
    val cached: Boolean = false,
    val progress: Double = 0.0,
    @SerialName("download_state") val downloadState: String? = null,
    val files: List<TorBoxFile> = emptyList()
)

@Serializable
data class TorBoxFile(
    val id: Long = 0,
    val name: String? = null,
    @SerialName("short_name") val shortName: String? = null,
    val size: Long = 0,
    @SerialName("mimetype") val mimeType: String? = null
)

/** One entry from GET /v1/api/torrents/checkcached (instant availability). */
@Serializable
data class CachedHash(
    val hash: String? = null,
    val name: String? = null
)
