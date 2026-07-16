package com.streambert.tv.data.addons

import com.streambert.tv.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Manages installed Stremio addons. All add-ons — stream and subtitle alike —
 * live in one list, persisted in the "scraper add-ons" setting (so the
 * TorBox/Real-Debrid resolver keeps working unchanged). This class adds manifest
 * fetching + add/remove/reorder used by the Add-ons manager UI and the QR
 * phone-management server.
 */
class AddonRepository(
    private val api: AddonApi,
    private val settings: SettingsRepository
) {

    /** Every installed add-on base URL (one per line in the single add-ons list). */
    suspend fun streamAddonUrls(): List<String> =
        settings.currentScraperAddons().lineSequence()
            .map { it.trim() }.filter { it.startsWith("http") }.toList()

    /**
     * Fetch manifests for all installed add-ons (best-effort; null manifest if
     * unreachable) and classify each as a subtitle or stream add-on from its
     * manifest's declared resources.
     */
    suspend fun loadInstalled(): List<InstalledAddon> = coroutineScope {
        streamAddonUrls().map { url ->
            async(Dispatchers.IO) {
                val manifest = fetchManifest(url)
                val kind = if (manifest?.providesSubtitles() == true && manifest.providesStreams() != true) {
                    AddonKind.SUBTITLE
                } else {
                    AddonKind.STREAM
                }
                InstalledAddon(url, manifest, kind)
            }
        }.map { it.await() }
    }

    /** Fetch a single manifest, or null on failure. */
    suspend fun fetchManifest(baseOrManifestUrl: String): AddonManifest? = withContext(Dispatchers.IO) {
        runCatching { api.getManifest(manifestUrl(baseOrManifestUrl)) }.getOrNull()
    }

    suspend fun addStreamAddon(url: String) {
        val u = url.trim()
        if (!u.startsWith("http")) return
        val list = streamAddonUrls().toMutableList()
        if (list.none { it.equals(u, true) }) {
            list.add(u)
            persist(list)
        }
    }

    suspend fun removeStreamAddon(url: String) {
        val list = streamAddonUrls().toMutableList()
        list.removeAll { it.equals(url.trim(), true) }
        persist(list)
    }

    suspend fun moveStreamAddon(index: Int, up: Boolean) {
        val list = streamAddonUrls().toMutableList()
        val target = if (up) index - 1 else index + 1
        if (index in list.indices && target in list.indices) {
            val tmp = list[index]; list[index] = list[target]; list[target] = tmp
            persist(list)
        }
    }

    /** Replace the entire stream-addon list (used by the QR web manager). */
    suspend fun setStreamAddons(urls: List<String>) =
        persist(urls.map { it.trim() }.filter { it.startsWith("http") }.distinct())

    private suspend fun persist(list: List<String>) {
        settings.setScraperAddons(list.joinToString("\n"))
    }

    /** Normalize any base/manifest URL into a fetchable manifest.json URL. */
    private fun manifestUrl(raw: String): String {
        var u = raw.trim()
        if (!u.startsWith("http")) u = "https://$u"
        return if (u.endsWith("manifest.json", true)) u else u.trimEnd('/') + "/manifest.json"
    }
}
