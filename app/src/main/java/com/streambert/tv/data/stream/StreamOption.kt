package com.streambert.tv.data.stream

/**
 * A single user-selectable stream (one release/quality) for the sources panel.
 *
 * @param url        directly-playable (debrid-resolved) URL
 * @param label      full release title (filename / details line)
 * @param qualityLabel short quality tag, e.g. "4K", "1080p", "720p", "SD"
 * @param quality    numeric quality used for sorting (2160/1080/720/480/0)
 * @param cached     true when the addon marked it as already cached on TorBox
 */
data class StreamOption(
    val url: String? = null,
    val hash: String? = null,
    val label: String,
    val qualityLabel: String,
    val quality: Int,
    val cached: Boolean,
    val instant: Boolean = false,
    /** Add-on this release came from, e.g. "Torrentio", "Comet". */
    val provider: String = "",
    /**
     * Debrid service this option plays through: "TorBox" or "RD" (null = resolve
     * via whichever service is configured). Lets the picker show TorBox and
     * Real-Debrid as separate, explicitly-selectable options.
     */
    val debrid: String? = null,
    val badges: List<String> = emptyList(),
    /** Human size, e.g. "5.6 GB", parsed from the release title (null if unknown). */
    val sizeLabel: String? = null,
    /** File container, e.g. "MKV" / "MP4" (null if unknown). */
    val container: String? = null,
    /** Audio/track language hint shown in the picker; defaults to "Original". */
    val language: String = "Original",
    /** Seeder count parsed from the release title (0 if unknown). Used as the
     *  speed tiebreaker when auto-picking the fastest source. */
    val seeders: Int = 0
)
