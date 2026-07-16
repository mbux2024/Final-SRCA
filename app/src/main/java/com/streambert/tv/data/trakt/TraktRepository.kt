package com.streambert.tv.data.trakt

import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Reads the signed-in user's Trakt library. Trakt only returns ids, so each
 * entry is resolved to a rich [CatalogItem] via TMDB (poster/backdrop/rating),
 * preserving the user's Trakt ordering (most-recently-added first).
 */
class TraktRepository(
    private val api: TraktApi,
    private val auth: TraktAuthRepository,
    private val tmdb: TmdbRepository
) {

    /** The user's Trakt watchlist (movies + shows), newest first. */
    suspend fun watchlist(): List<CatalogItem> = coroutineScope {
        val header = auth.validAuthHeader() ?: return@coroutineScope emptyList()

        val movies = async { fetch(header, "movies", MediaType.MOVIE) }
        val shows = async { fetch(header, "shows", MediaType.TV) }

        // Interleave by "listed_at" (desc) so the row reflects add order.
        val combined = (movies.await() + shows.await())
            .sortedByDescending { it.listedAt ?: "" }

        // Resolve TMDB metadata in parallel, keeping order + de-duping.
        val resolved = combined.map { entry ->
            async { tmdb.catalogItemFromTmdb(entry.tmdbId, entry.type) }
        }.awaitAll()

        resolved.filterNotNull().distinctBy { "${it.type}_${it.id}" }
    }

    private suspend fun fetch(header: String, type: String, mediaType: MediaType): List<Entry> {
        val resp = runCatching { api.getWatchlist(header, type) }.getOrNull() ?: return emptyList()
        if (!resp.isSuccessful) return emptyList()
        return resp.body().orEmpty().mapNotNull { item ->
            val media = item.movie ?: item.show
            val tmdbId = media?.ids?.tmdb ?: return@mapNotNull null
            Entry(tmdbId = tmdbId, type = mediaType, listedAt = item.listedAt)
        }
    }

    private data class Entry(val tmdbId: Int, val type: MediaType, val listedAt: String?)
}
