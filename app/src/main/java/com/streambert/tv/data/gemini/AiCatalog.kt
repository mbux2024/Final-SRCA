package com.streambert.tv.data.gemini

import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.data.tmdb.TmdbRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * AI-powered catalog features built on Gemini + TMDB:
 *  - [search]: natural-language / spoken query -> matching titles.
 *  - [recommend]: personalized picks from titles the user already likes.
 *
 * Gemini only ever proposes real title names; every suggestion is then resolved
 * against TMDB, so the app always displays genuine catalog items (poster, ids,
 * ratings, playability) and never surfaces model-invented metadata. All methods
 * fail soft (return empty) so AI is a pure enhancement over the normal flow.
 */
class AiCatalog(
    private val gemini: GeminiRepository,
    private val tmdb: TmdbRepository
) {
    @Serializable
    data class Suggestion(
        val title: String = "",
        val year: Int? = null,
        val type: String? = null
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun isAvailable(): Boolean = gemini.isConfigured()

    /** Interpret a free-form / spoken query and return matching catalog titles. */
    suspend fun search(query: String, limit: Int = 24): List<CatalogItem> {
        if (query.isBlank()) return emptyList()
        val prompt = buildString {
            appendLine("You are a movie and TV search assistant for a streaming app.")
            appendLine("Interpret the user's request — it may describe a mood, plot, era, actor, or ask for titles similar to something — and list real, existing movies or TV shows that best match.")
            appendLine("Return ONLY a JSON array (no prose, no markdown) of up to $limit objects with keys:")
            appendLine("\"title\" (exact official title), \"year\" (integer release/first-air year), \"type\" (\"movie\" or \"tv\").")
            appendLine("Order best matches first. Do not include anime.")
            appendLine("User request: \"\"\"$query\"\"\"")
        }
        val raw = gemini.generate(prompt, temperature = 0.4, jsonOutput = true) ?: return emptyList()
        return resolve(parse(raw), limit)
    }

    /** Recommend titles the user would enjoy, based on titles they already like. */
    suspend fun recommend(likedTitles: List<String>, limit: Int = 20): List<CatalogItem> {
        val liked = likedTitles.filter { it.isNotBlank() }.distinct().take(12)
        if (liked.isEmpty()) return emptyList()
        val prompt = buildString {
            appendLine("You are a recommendation engine for a movie/TV streaming app.")
            appendLine("Given the titles the user likes, recommend other real movies or TV shows they'd probably enjoy.")
            appendLine("Do NOT include any of the titles they already like, and do not include anime.")
            appendLine("Return ONLY a JSON array (no prose, no markdown) of up to $limit objects with keys:")
            appendLine("\"title\" (exact official title), \"year\" (integer), \"type\" (\"movie\" or \"tv\").")
            appendLine("Titles the user likes: ${liked.joinToString("; ")}")
        }
        val raw = gemini.generate(prompt, temperature = 0.9, jsonOutput = true) ?: return emptyList()
        return resolve(parse(raw), limit)
    }

    private fun parse(raw: String): List<Suggestion> {
        // Isolate the JSON array in case the model wrapped it in prose/fences.
        val start = raw.indexOf('[')
        val end = raw.lastIndexOf(']')
        val text = if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
        return runCatching { json.decodeFromString(ListSerializer(Suggestion.serializer()), text) }
            .getOrDefault(emptyList())
            .filter { it.title.isNotBlank() }
    }

    private suspend fun resolve(suggestions: List<Suggestion>, limit: Int): List<CatalogItem> =
        coroutineScope {
            suggestions.take(limit + 8).map { s ->
                async { runCatching { bestMatch(s) }.getOrNull() }
            }.awaitAll()
                .filterNotNull()
                .distinctBy { "${it.type}_${it.id}" }
                .take(limit)
        }

    /** Resolve one AI suggestion to a real TMDB catalog item (best title/type/year match). */
    private suspend fun bestMatch(s: Suggestion): CatalogItem? {
        val results = tmdb.search(s.title)
        if (results.isEmpty()) return null
        val wantType = when (s.type?.lowercase()) {
            "tv", "show", "series" -> MediaType.TV
            "movie", "film" -> MediaType.MOVIE
            else -> null
        }
        val titleLc = s.title.trim().lowercase()
        return results.firstOrNull {
            it.title.lowercase() == titleLc &&
                (wantType == null || it.type == wantType) &&
                (s.year == null || it.year?.toIntOrNull() == s.year)
        } ?: results.firstOrNull {
            it.title.lowercase() == titleLc && (wantType == null || it.type == wantType)
        } ?: results.firstOrNull { wantType == null || it.type == wantType }
        ?: results.first()
    }
}
