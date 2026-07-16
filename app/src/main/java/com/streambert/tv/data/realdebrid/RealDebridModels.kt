package com.streambert.tv.data.realdebrid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** POST /torrents/addMagnet -> { id, uri } */
@Serializable
data class RdAddTorrent(
    val id: String? = null,
    val uri: String? = null
)

/** GET /torrents/info/{id} */
@Serializable
data class RdTorrentInfo(
    val id: String? = null,
    val filename: String? = null,
    val hash: String? = null,
    val bytes: Long = 0,
    val status: String? = null,
    val progress: Double = 0.0,
    val files: List<RdFile> = emptyList(),
    val links: List<String> = emptyList()
)

@Serializable
data class RdFile(
    val id: Long = 0,
    val path: String? = null,
    val bytes: Long = 0,
    // RD marks a file selected with 1, unselected with 0.
    val selected: Int = 0
) {
    /** Last path segment (the actual filename). */
    val displayName: String
        get() = path?.trim('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: path.orEmpty()
}

/** POST /unrestrict/link -> { download, filename, filesize, ... } */
@Serializable
data class RdUnrestrictLink(
    val id: String? = null,
    val filename: String? = null,
    val filesize: Long = 0,
    val link: String? = null,
    val download: String? = null,
    @SerialName("streamable") val streamable: Int = 0
)
