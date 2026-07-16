package com.streambert.tv.data.stream

import com.streambert.tv.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** A side-loadable subtitle track resolved from a Stremio subtitles addon. */
data class SubtitleTrack(
    val url: String,
    val language: String,
    val mimeType: String,
    val displayLanguage: String = language,
    /** Provider-supplied id, e.g. "subsense-srt-opensubtitles-eng-1". */
    val id: String = "",
    /** Where it came from, e.g. "SubSense" (shown as a badge in the picker). */
    val source: String = ""
)

/**
 * Fetches subtitles for a title from every installed Stremio add-on that exposes
 * a subtitles resource (managed through the single Add-ons list — there is no
 * separate subtitle-addon slot). For each add-on:
 *   GET {base}subtitles/{movie|series}/{id}.json   (series id = tt...:season:episode)
 * Stream-only add-ons simply 404 and are skipped; results are merged.
 */
class SubtitleRepository(
    private val api: StremioApi,
    private val settings: SettingsRepository
) {

    suspend fun fetch(imdbId: String, season: Int? = null, episode: Int? = null): List<SubtitleTrack> {
        if (imdbId.isBlank()) return emptyList()
        val bases = settings.addonBaseUrls()
        if (bases.isEmpty()) return emptyList()

        val type = if (season != null && episode != null) "series" else "movie"
        val id = if (type == "series") "$imdbId:$season:$episode" else imdbId

        // Query every installed add-on in parallel; ignore the ones that don't serve subtitles.
        val all = coroutineScope {
            bases.map { base ->
                async(Dispatchers.IO) { fetchFrom(base, type, id) }
            }.flatMap { it.await() }
        }.distinctBy { it.url }

        // English only (fall back to everything if no English was returned).
        val english = all.filter { isEnglish(it.language) }
        return (if (english.isNotEmpty()) english else all).take(30)
    }

    private suspend fun fetchFrom(base: String, type: String, id: String): List<SubtitleTrack> {
        val url = "${base}subtitles/$type/$id.json"
        val response = try {
            api.getSubtitles(url)
        } catch (_: Exception) {
            return emptyList()
        }
        return response.subtitles
            .filter { !it.url.isNullOrBlank() }
            .map { s ->
                val lang = s.lang?.ifBlank { "und" } ?: "und"
                val source = sourceFromUrl(s.url!!)
                SubtitleTrack(
                    url = s.url!!,
                    language = lang,
                    mimeType = mimeFor(s.url),
                    // "English · SubSense" — language + where it's from (Nuvio-style).
                    displayLanguage = LanguageNames.displayName(lang) + (source?.let { " · $it" } ?: ""),
                    id = s.id?.takeIf { it.isNotBlank() } ?: subtitleIdFrom(s.url!!, lang),
                    source = source ?: "Add-on"
                )
            }
            .distinctBy { it.url }
    }

    private fun isEnglish(lang: String): Boolean {
        val l = lang.lowercase()
        return l == "en" || l == "eng" || l.startsWith("en-") || l.startsWith("en_")
    }

    private fun sourceFromUrl(url: String): String? {
        val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase() ?: return null
        return when {
            host.contains("subsense") || host.contains("nepiraw") -> "SubSense"
            host.contains("opensubtitles") -> "OpenSubtitles"
            host.contains("subdl") -> "SubDL"
            host.contains("wyzie") -> "Wyzie"
            host.contains("subsource") -> "SubSource"
            else -> host.removePrefix("www.").substringBefore('.')
                .replaceFirstChar { it.uppercase() }
                .takeIf { it.isNotBlank() }
        }
    }

    /** Fallback subtitle id (from the file name) when the add-on omits one. */
    private fun subtitleIdFrom(url: String, lang: String): String {
        val last = url.substringAfterLast('/').substringBefore('?').substringBeforeLast('.')
        return last.takeIf { it.isNotBlank() } ?: lang
    }

    private fun mimeFor(url: String): String {
        val u = url.lowercase()
        return when {
            u.contains(".vtt") -> "text/vtt"
            u.contains(".ssa") || u.contains(".ass") -> "text/x-ssa"
            else -> "application/x-subrip" // OpenSubtitles serves .srt by default
        }
    }
}
