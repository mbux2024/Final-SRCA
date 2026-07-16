package com.streambert.tv.data.addons

import retrofit2.http.GET
import retrofit2.http.Url

/** Fetches a Stremio addon manifest from an absolute URL. */
interface AddonApi {
    @GET
    suspend fun getManifest(@Url url: String): AddonManifest
}
