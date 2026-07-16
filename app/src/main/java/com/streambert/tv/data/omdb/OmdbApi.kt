package com.streambert.tv.data.omdb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** Minimal OMDb client for real IMDb ratings, keyed by IMDb id. */
interface OmdbApi {
    @GET("/")
    suspend fun byImdbId(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String
    ): OmdbResponse
}

@Serializable
data class OmdbResponse(
    @SerialName("imdbRating") val imdbRating: String? = null,
    @SerialName("imdbVotes") val imdbVotes: String? = null,
    @SerialName("Response") val response: String? = null
) {
    /** Parsed rating (e.g. "8.5" -> 8.5), or null when unavailable ("N/A"). */
    val rating: Double?
        get() = imdbRating?.takeIf { it != "N/A" }?.toDoubleOrNull()
}
