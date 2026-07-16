package com.streambert.tv.data.omdb

import com.streambert.tv.data.settings.SettingsRepository

/** Fetches the real IMDb rating for a title via OMDb (needs an OMDb API key). */
class OmdbRepository(
    private val api: OmdbApi,
    private val settings: SettingsRepository
) {
    private val cache = HashMap<String, Double?>()

    suspend fun imdbRating(imdbId: String): Double? {
        if (imdbId.isBlank()) return null
        cache[imdbId]?.let { return it }
        val key = settings.currentOmdbKey()
        if (key.isBlank()) return null
        val rating = try {
            api.byImdbId(imdbId, key).rating
        } catch (e: Exception) {
            null
        }
        cache[imdbId] = rating
        return rating
    }
}
