package com.streambert.tv.data.tmdb

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * TMDB v3 endpoints. Auth is via the v4 read-access bearer token injected by
 * the OkHttp interceptor in NetworkModule, matching the desktop app.
 */
interface TmdbApi {

    @GET("trending/movie/week")
    suspend fun trendingMovies(@Query("language") language: String): TmdbPage<TmdbResult>

    @GET("trending/tv/week")
    suspend fun trendingTv(@Query("language") language: String): TmdbPage<TmdbResult>

    @GET("trending/movie/day")
    suspend fun trendingMoviesToday(@Query("language") language: String): TmdbPage<TmdbResult>

    @GET("trending/tv/day")
    suspend fun trendingTvToday(@Query("language") language: String): TmdbPage<TmdbResult>

    @GET("tv/airing_today")
    suspend fun airingTodayTv(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("discover/movie")
    suspend fun discoverMoviesByGenre(
        @Query("language") language: String,
        @Query("with_genres") genres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("discover/tv")
    suspend fun discoverTvByGenre(
        @Query("language") language: String,
        @Query("with_genres") genres: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("language") language: String,
        @Query("with_watch_providers") providers: String,
        @Query("watch_region") region: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("primary_release_date.lte") releaseDateLte: String? = null,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("language") language: String,
        @Query("with_watch_providers") providers: String,
        @Query("watch_region") region: String,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("vote_count.gte") minVotes: Int? = null,
        @Query("first_air_date.lte") firstAirDateLte: String? = null,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("movie/{id}/videos")
    suspend fun movieVideos(@Path("id") id: Int, @Query("language") language: String): VideosResponse

    @GET("tv/{id}/videos")
    suspend fun tvVideos(@Path("id") id: Int, @Query("language") language: String): VideosResponse

    @GET("movie/{id}/credits")
    suspend fun movieCredits(@Path("id") id: Int, @Query("language") language: String): CreditsResponse

    @GET("tv/{id}/credits")
    suspend fun tvCredits(@Path("id") id: Int, @Query("language") language: String): CreditsResponse

    @GET("watch/providers/movie")
    suspend fun watchProvidersMovie(
        @Query("language") language: String,
        @Query("watch_region") region: String
    ): ProvidersResponse

    @GET("watch/providers/tv")
    suspend fun watchProvidersTv(
        @Query("language") language: String,
        @Query("watch_region") region: String
    ): ProvidersResponse

    @GET("movie/popular")
    suspend fun popularMovies(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("tv/popular")
    suspend fun popularTv(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("movie/now_playing")
    suspend fun nowPlayingMovies(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("movie/upcoming")
    suspend fun upcomingMovies(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("movie/top_rated")
    suspend fun topRatedMovies(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("tv/top_rated")
    suspend fun topRatedTv(
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("search/multi")
    suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String,
        @Query("page") page: Int = 1
    ): TmdbPage<TmdbResult>

    @GET("movie/{id}")
    suspend fun movieDetails(
        @Path("id") id: Int,
        @Query("language") language: String,
        @Query("append_to_response") append: String = "release_dates"
    ): MovieDetails

    @GET("tv/{id}")
    suspend fun tvDetails(
        @Path("id") id: Int,
        @Query("language") language: String,
        @Query("append_to_response") append: String = "content_ratings"
    ): TvDetails

    @GET("tv/{id}/season/{season}")
    suspend fun seasonDetails(
        @Path("id") id: Int,
        @Path("season") season: Int,
        @Query("language") language: String
    ): SeasonDetails

    @GET("movie/{id}/external_ids")
    suspend fun movieExternalIds(@Path("id") id: Int): ExternalIds

    @GET("tv/{id}/external_ids")
    suspend fun tvExternalIds(@Path("id") id: Int): ExternalIds

    @GET("movie/{id}/recommendations")
    suspend fun movieRecommendations(
        @Path("id") id: Int,
        @Query("language") language: String
    ): TmdbPage<TmdbResult>

    @GET("tv/{id}/recommendations")
    suspend fun tvRecommendations(
        @Path("id") id: Int,
        @Query("language") language: String
    ): TmdbPage<TmdbResult>

    @GET("person/{id}")
    suspend fun personDetails(
        @Path("id") id: Int,
        @Query("language") language: String
    ): PersonDetails

    @GET("person/{id}/combined_credits")
    suspend fun personCombinedCredits(
        @Path("id") id: Int,
        @Query("language") language: String
    ): PersonCombinedCredits
}
