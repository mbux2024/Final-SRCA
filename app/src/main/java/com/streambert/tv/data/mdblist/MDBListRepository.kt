package com.streambert.tv.data.mdblist

import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.settings.SettingsRepository
import java.util.Locale

/**
 * Fetches multi-source ratings (IMDb, TMDB, Rotten Tomatoes, Metacritic, Trakt,
 * Letterboxd, audience) for a title from MDBList. Needs an MDBList API key.
 */
class MDBListRepository(
    private val api: MDBListApi,
    private val settings: SettingsRepository
) {
    // Cache the RAW MDBList response per title (not the filtered/formatted result).
    // This means: (1) provider on/off toggles are applied on every read, so
    // changing them takes effect without a refetch; (2) we make ONE API call per
    // title shared by ratings() and imdbRating(); (3) we never cache a failed or
    // empty fetch, so a transient error (e.g. MDBList rate-limit) doesn't hide a
    // title's ratings for the rest of the session — the next visit retries.
    private val cache = HashMap<String, List<MdbRatingDto>>()

    /** Shared fetch: returns cached raw ratings, or fetches once. Failures are not cached. */
    private suspend fun rawRatings(tmdbId: Int, type: MediaType): List<MdbRatingDto> {
        val key = settings.currentMdblistKey().trim()
        if (key.isBlank() || tmdbId <= 0) return emptyList()
        val mediaType = if (type == MediaType.TV) "show" else "movie"
        val cacheKey = "$mediaType/$tmdbId"
        cache[cacheKey]?.let { return it }

        val raw = try {
            api.ratingsByTmdb(mediaType, tmdbId, key).ratings
        } catch (e: Exception) {
            emptyList()
        }
        // Only cache a real, non-empty result so failures can be retried later.
        if (raw.isNotEmpty()) cache[cacheKey] = raw
        return raw
    }

    /** Rating badges for the detail page, or empty if disabled / no key / no data. */
    suspend fun ratings(tmdbId: Int, type: MediaType): List<RatingBadge> {
        if (!settings.currentMdblistEnabled()) return emptyList()
        val badges = rawRatings(tmdbId, type)
            .mapNotNull { it.toBadge() }
            .let { filterByProviderToggles(it) }
        // Keep a stable, meaningful order.
        val order = listOf("IMDb", "Rotten Tomatoes", "Audience", "Metacritic", "TMDB", "Trakt", "Letterboxd")
        return badges.sortedBy { order.indexOf(it.label).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    }

    private suspend fun filterByProviderToggles(badges: List<RatingBadge>): List<RatingBadge> {
        val showImdb = settings.currentMdbShowImdb()
        val showTomatoes = settings.currentMdbShowTomatoes()
        val showAudience = settings.currentMdbShowAudience()
        val showMetacritic = settings.currentMdbShowMetacritic()
        val showTmdb = settings.currentMdbShowTmdb()
        val showTrakt = settings.currentMdbShowTrakt()
        val showLetterboxd = settings.currentMdbShowLetterboxd()
        return badges.filter { b ->
            when (b.source) {
                "imdb" -> showImdb
                "tomatoes" -> showTomatoes
                "audience" -> showAudience
                "metacritic" -> showMetacritic
                "tmdb" -> showTmdb
                "trakt" -> showTrakt
                "letterboxd" -> showLetterboxd
                else -> true
            }
        }
    }

    /** The IMDb rating (0–10) if MDBList has it — used to enrich the detail page.
     *  Reuses the shared per-title cache, so it doesn't cost a second API call. */
    suspend fun imdbRating(tmdbId: Int, type: MediaType): Double? =
        rawRatings(tmdbId, type)
            .firstOrNull { it.source.equals("imdb", ignoreCase = true) }
            ?.value

    private fun MdbRatingDto.toBadge(): RatingBadge? {
        val v = value ?: return null
        val src = source?.lowercase(Locale.ROOT) ?: return null
        return when (src) {
            "imdb" -> RatingBadge(src, "IMDb", String.format(Locale.US, "%.1f", v))
            "letterboxd" -> RatingBadge(src, "Letterboxd", String.format(Locale.US, "%.1f", v))
            "tmdb" -> RatingBadge(src, "TMDB", "${percent(v)}%")
            "trakt" -> RatingBadge(src, "Trakt", "${percent(v)}%")
            "metacritic" -> RatingBadge(src, "Metacritic", "${percent(v)}")
            "tomatoes" -> RatingBadge(src, "Rotten Tomatoes", "${percent(v)}%")
            "tomatoesaudience", "audience" -> RatingBadge("audience", "Audience", "${percent(v)}%")
            else -> null
        }
    }

    private fun percent(v: Double): Int = v.toInt()
}
