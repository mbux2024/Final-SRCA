package com.streambert.tv.data.badges

import com.streambert.tv.data.NetworkModule
import com.streambert.tv.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Loads a NuvioTV-format stream-badge config from a user-configured URL and
 * matches release titles against its regex filters.
 *
 * The config is fetched once per URL and cached in memory for the process
 * lifetime. Matching is a cheap regex scan over the release title, so it is
 * done on demand from the sources panel.
 */
class BadgeRepository(
    private val settings: SettingsRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val mutex = Mutex()
    private var cachedUrl: String? = null
    private var cachedFilters: List<CompiledBadge> = emptyList()

    /** Returns the compiled badge filters for the currently-configured URL. */
    suspend fun filters(): List<CompiledBadge> = mutex.withLock {
        val url = settings.currentBadgeConfigUrl().trim()
        if (url.isBlank()) return@withLock emptyList()
        if (url == cachedUrl) return@withLock cachedFilters
        val compiled = withContext(Dispatchers.IO) { fetchAndCompile(url) }
        cachedUrl = url
        cachedFilters = compiled
        compiled
    }

    /** Force a re-fetch on the next [filters] call (e.g. after the URL changes). */
    fun invalidate() {
        cachedUrl = null
        cachedFilters = emptyList()
    }

    /** Badges whose pattern matches [releaseTitle], de-duplicated by image. */
    fun match(releaseTitle: String, filters: List<CompiledBadge>): List<StreamBadge> {
        if (filters.isEmpty() || releaseTitle.isBlank()) return emptyList()
        val out = LinkedHashMap<String, StreamBadge>()
        filters.forEach { f ->
            if (f.matches(releaseTitle)) {
                val key = f.badge.dedupeKey
                if (key !in out) out[key] = f.badge
            }
        }
        return out.values.toList()
    }

    private fun fetchAndCompile(url: String): List<CompiledBadge> {
        val body = runCatching {
            client.newCall(
                Request.Builder()
                    .url(url)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("User-Agent", "Mozilla/5.0 (Android TV) KnightFlix")
                    .build()
            )
                .execute()
                .use { resp -> if (resp.isSuccessful) resp.body?.string() else null }
        }.getOrNull() ?: return emptyList()

        val payload = runCatching {
            NetworkModule.json.decodeFromString(BadgePayload.serializer(), body)
        }.getOrNull() ?: return emptyList()

        return payload.filters.mapNotNull { dto -> compile(dto) }
    }

    private fun compile(dto: BadgeFilterDto): CompiledBadge? {
        val name = dto.name?.trim().orEmpty()
        val pattern = dto.pattern?.trim().orEmpty()
        val enabled = dto.isEnabled ?: true
        val image = dto.imageURL?.trim().orEmpty()
        if (!enabled || name.isBlank() || pattern.isBlank() || image.isBlank()) return null

        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return null
        return CompiledBadge(
            badge = StreamBadge(
                name = name,
                imageUrl = image,
                tagColor = dto.tagColor.orEmpty(),
                tagStyle = dto.tagStyle.orEmpty(),
                textColor = dto.textColor.orEmpty(),
                borderColor = dto.borderColor.orEmpty()
            ),
            regex = regex,
            literalHint = extractLiteralHint(pattern)
        )
    }

    /** Cheap pre-screen literal so we skip regex when the title obviously can't match. */
    private fun extractLiteralHint(pattern: String): String? {
        val meta = "\\[](){}*+?|^$."
        if (pattern.length >= 2 && pattern.none { it in meta }) return pattern
        if ('|' in pattern) return null
        val stripped = pattern
            .replace("\\b", "")
            .replace("(?i)", "")
            .replace("(?:", "")
            .replace("(", "")
            .replace(")", "")
        return stripped.takeIf { it.length >= 2 && it.none { c -> c in meta } }
    }
}
