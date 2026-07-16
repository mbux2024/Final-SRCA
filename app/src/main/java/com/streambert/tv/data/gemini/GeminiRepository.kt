package com.streambert.tv.data.gemini

import com.streambert.tv.data.settings.SettingsRepository

/**
 * Thin client over the Gemini API. Reads the user's key from settings
 * (Settings screen or local.properties/BuildConfig) on each call, so a newly
 * entered key applies without a restart. Returns null on any failure so callers
 * can fall back gracefully — Gemini is always an optional enhancement.
 */
class GeminiRepository(
    private val api: GeminiApi,
    private val settings: SettingsRepository
) {

    /** True if a Gemini key is configured. */
    suspend fun isConfigured(): Boolean = settings.currentGeminiKey().isNotBlank()

    /**
     * Send a single prompt and return the model's text reply, or null if there's
     * no key / the request fails.
     */
    suspend fun generate(
        prompt: String,
        model: String = DEFAULT_MODEL,
        temperature: Double = 0.7,
        maxOutputTokens: Int? = null,
        jsonOutput: Boolean = false
    ): String? {
        val key = settings.currentGeminiKey().trim()
        if (key.isBlank()) return null
        return try {
            val response = api.generateContent(
                model = model,
                apiKey = key,
                body = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                    generationConfig = GeminiGenerationConfig(
                        temperature = temperature,
                        maxOutputTokens = maxOutputTokens,
                        responseMimeType = if (jsonOutput) "application/json" else null
                    )
                )
            )
            response.candidates
                .firstOrNull()
                ?.content
                ?.parts
                ?.joinToString("") { it.text }
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Fast, cheap, and available on the free tier. */
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}
