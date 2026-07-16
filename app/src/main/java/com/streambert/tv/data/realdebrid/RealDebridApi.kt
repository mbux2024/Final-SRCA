package com.streambert.tv.data.realdebrid

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Real-Debrid REST API (host: https://api.real-debrid.com/rest/1.0/).
 *
 * Direct-resolution flow (mirrors our TorBox flow, but with RD's endpoints):
 *   1. addMagnet(magnet)          -> torrent id
 *   2. getTorrentInfo(id)         -> file list (pick the episode/movie)
 *   3. selectFiles(id, fileId)    -> tell RD which file to cache/serve
 *   4. getTorrentInfo(id)         -> when status == "downloaded", read links[]
 *   5. unrestrictLink(link)       -> a direct HTTPS stream URL
 *
 * Auth is a personal API token sent as `Authorization: Bearer <token>`.
 */
interface RealDebridApi {

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") authorization: String,
        @Field("magnet") magnet: String
    ): Response<RdAddTorrent>

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): Response<RdTorrentInfo>

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") authorization: String,
        @Path("id") id: String,
        @Field("files") files: String
    ): Response<ResponseBody>

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") authorization: String,
        @Field("link") link: String
    ): Response<RdUnrestrictLink>

    @DELETE("torrents/delete/{id}")
    suspend fun deleteTorrent(
        @Header("Authorization") authorization: String,
        @Path("id") id: String
    ): Response<ResponseBody>
}
