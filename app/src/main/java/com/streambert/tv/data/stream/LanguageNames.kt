package com.streambert.tv.data.stream

import java.util.Locale

/**
 * Maps subtitle/audio language codes (ISO 639-1/639-2, with optional region)
 * to human-readable names, e.g. "en" -> "English", "pt-br" -> "Portuguese (Brazil)".
 * Mirrors the language-name display NuvioTV shows in its subtitle picker.
 */
object LanguageNames {

    private val extra = mapOf(
        "pt-br" to "Portuguese (Brazil)",
        "pt-pt" to "Portuguese (Portugal)",
        "zh-cn" to "Chinese (Simplified)",
        "zh-tw" to "Chinese (Traditional)",
        "es-419" to "Spanish (Latin America)",
        "es-es" to "Spanish (Spain)",
        "forced" to "Forced",
        "und" to "Unknown"
    )

    fun displayName(codeRaw: String?): String {
        val code = codeRaw?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (code.isBlank()) return "Unknown"
        extra[code]?.let { return it }

        // Split off any region (e.g. "en-US") and resolve the base language.
        val base = code.substringBefore('-').substringBefore('_')
        val name = runCatching { Locale(base).getDisplayLanguage(Locale.ENGLISH) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(base, ignoreCase = true) }
            ?: return codeRaw ?: "Unknown"

        val region = code.substringAfter('-', "").substringAfter('_', "")
        return if (region.isNotBlank()) "$name (${region.uppercase(Locale.ROOT)})" else name
    }
}
