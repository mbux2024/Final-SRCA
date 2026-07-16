package com.streambert.tv.data.mdblist

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * MDBList API (host: https://api.mdblist.com/).
 *
 * Returns aggregated ratings for a title from many sources (IMDb, TMDB,
 * Rotten Tomatoes, Metacritic, Trakt, Letterboxd, audience). Needs a free
 * MDBList API key.
 *
 *   GET /tmdb/{mediaType}/{tmdbId}?apikey=KEY   (mediaType = movie | show)
 */
interface MDBListApi {

    @GET("tmdb/{mediaType}/{tmdbId}")
    suspend fun ratingsByTmdb(
        @Path("mediaType") mediaType: String,
        @Path("tmdbId") tmdbId: Int,
        @Query("apikey") apiKey: String
    ): MdbRatingsResponse
}
