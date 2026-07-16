package com.streambert.tv.data.addons

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A Stremio addon manifest (manifest.json). `resources` may be a list of plain
 * strings ("stream") or objects ({"name":"stream","types":[...]}), so it is kept
 * as raw JSON and normalized via [resourceNames].
 */
@Serializable
data class AddonManifest(
    val id: String? = null,
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val types: List<String> = emptyList(),
    val catalogs: List<AddonCatalogDesc> = emptyList(),
    val resources: List<JsonElement> = emptyList(),
    val idPrefixes: List<String> = emptyList()
) {
    val resourceNames: List<String>
        get() = resources.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> el["name"]?.jsonPrimitive?.contentOrNull
                else -> null
            }
        }

    fun providesStreams(): Boolean = resourceNames.any { it.equals("stream", true) }
    fun providesSubtitles(): Boolean = resourceNames.any { it.equals("subtitles", true) }
    fun providesCatalogs(): Boolean = catalogs.isNotEmpty()
}

@Serializable
data class AddonCatalogDesc(
    val type: String? = null,
    val id: String? = null,
    val name: String? = null
)

/** An installed addon: its base URL plus the fetched manifest (null if unreachable). */
data class InstalledAddon(
    val baseUrl: String,
    val manifest: AddonManifest?,
    val kind: AddonKind
) {
    val displayName: String get() = manifest?.name?.takeIf { it.isNotBlank() } ?: fallbackName()
    val version: String get() = manifest?.version.orEmpty()
    val typeSummary: String
        get() {
            val types = manifest?.types.orEmpty()
            val res = manifest?.resourceNames.orEmpty()
            val parts = buildList {
                if (res.any { it.equals("stream", true) }) add("Streams")
                if (res.any { it.equals("subtitles", true) }) add("Subtitles")
                if (manifest?.catalogs?.isNotEmpty() == true) add("${manifest.catalogs.size} catalogs")
                if (types.isNotEmpty()) add(types.joinToString("/"))
            }
            return parts.joinToString(" · ").ifBlank { "Add-on" }
        }

    private fun fallbackName(): String = when {
        baseUrl.contains("comet", true) -> "Comet"
        baseUrl.contains("torrentio", true) -> "Torrentio"
        baseUrl.contains("opensubtitles", true) -> "OpenSubtitles"
        else -> baseUrl.substringAfter("://").substringBefore("/").ifBlank { "Add-on" }
    }
}

enum class AddonKind { STREAM, SUBTITLE }
