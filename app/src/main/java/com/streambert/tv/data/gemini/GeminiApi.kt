package com.streambert.tv.data.gemini

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Google Gemini (Generative Language) API.
 * Host: https://generativelanguage.googleapis.com/
 *
 *   POST v1beta/models/{model}:generateContent   (key via x-goog-api-key header)
 */
interface GeminiApi {

    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body body: GeminiRequest
    ): GeminiResponse
}
