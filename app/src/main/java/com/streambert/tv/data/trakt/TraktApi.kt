package com.streambert.tv.data.trakt

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Trakt.tv REST API (v2). The `trakt-api-version` and `trakt-api-key` headers
 * are attached by an OkHttp interceptor (see [com.streambert.tv.data.NetworkModule]);
 * per-request `Authorization: Bearer` headers are supplied by the caller.
 *
 * We return [Response] wrappers on the OAuth endpoints so the repository can
 * distinguish the device-flow states (pending / approved / expired / denied)
 * from the HTTP status code, exactly as Trakt documents.
 */
interface TraktApi {

    @POST("oauth/device/code")
    suspend fun requestDeviceCode(
        @Body body: TraktDeviceCodeRequest
    ): Response<TraktDeviceCode>

    @POST("oauth/device/token")
    suspend fun requestDeviceToken(
        @Body body: TraktDeviceTokenRequest
    ): Response<TraktToken>

    @POST("oauth/token")
    suspend fun refreshToken(
        @Body body: TraktRefreshTokenRequest
    ): Response<TraktToken>

    @POST("oauth/revoke")
    suspend fun revokeToken(
        @Body body: TraktRevokeRequest
    ): Response<Unit>

    @GET("users/settings")
    suspend fun getUserSettings(
        @Header("Authorization") authorization: String
    ): Response<TraktUserSettings>

    /** Watchlist for a media [type] ("movies" or "shows"). */
    @GET("sync/watchlist/{type}")
    suspend fun getWatchlist(
        @Header("Authorization") authorization: String,
        @Path("type") type: String,
        @Query("extended") extended: String = "full",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 100
    ): Response<List<TraktWatchlistItem>>
}
