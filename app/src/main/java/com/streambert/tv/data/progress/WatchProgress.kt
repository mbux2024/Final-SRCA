package com.streambert.tv.data.progress

import com.streambert.tv.data.model.MediaType
import kotlinx.serialization.Serializable

/**
 * A single resume/continue-watching entry, persisted as JSON.
 *
 * One entry per movie, or per (show, season, episode) for TV.
 */
@Serializable
data class WatchProgress(
    val key: String,
    val type: String,          // "movie" or "tv"
    val tmdbId: Int,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val season: Int = -1,
    val episode: Int = -1,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = 0,
    /** The exact source this title was played from, so resume replays it. */
    val streamUrl: String? = null,
    val streamHash: String? = null,
    /**
     * True for a "next episode up" placeholder that hasn't been started yet
     * (created when the previous episode finishes) so it still shows in
     * Continue Watching even though positionMs is 0.
     */
    val nextUp: Boolean = false
) {
    val mediaType: MediaType get() = MediaType.from(type)

    /** Fraction watched in 0f..1f (0 when duration unknown). */
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    companion object {
        /** Stable key used for upserts and lookups. */
        fun keyFor(type: MediaType, tmdbId: Int, season: Int, episode: Int): String =
            if (type == MediaType.TV) "tv_${tmdbId}_s${season}e$episode" else "movie_$tmdbId"
    }
}
