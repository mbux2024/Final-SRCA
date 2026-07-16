package com.streambert.tv.data.stream

import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Calls a Stremio addon's stream endpoint. We pass a fully-built absolute URL
 * via [Url] because the Stremio id format for series uses literal colons
 * (e.g. `tt0944947:1:1`) that must NOT be percent-encoded — building the URL
 * ourselves avoids Retrofit's @Path encoding.
 */
interface StremioApi {

    @GET
    suspend fun getStreams(@Url url: String): StremioStreamResponse

    @GET
    suspend fun getSubtitles(@Url url: String): StremioSubtitlesResponse
}
