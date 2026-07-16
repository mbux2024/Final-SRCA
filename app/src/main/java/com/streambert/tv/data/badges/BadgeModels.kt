package com.streambert.tv.data.badges

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * NuvioTV-compatible "stream badge" configuration.
 *
 * The badge config is a JSON document (exported from the NuvioTV badge editor)
 * that lists [BadgeFilterDto]s. Each filter carries a regular-expression
 * [pattern] that is tested against a release title, plus an [imageURL] pointing
 * at the badge artwork and optional tag/border colors. When a filter matches a
 * source's release title we render its badge image on the source card.
 *
 * Only the fields we actually use are modeled; unknown keys are ignored by the
 * lenient JSON parser in NetworkModule.
 */
@Serializable
data class BadgePayload(
    val filters: List<BadgeFilterDto> = emptyList(),
    val groups: List<BadgeGroupDto> = emptyList()
)

@Serializable
data class BadgeFilterDto(
    val id: String? = null,
    val groupId: String? = null,
    val name: String? = null,
    val pattern: String? = null,
    val imageURL: String? = null,
    val isEnabled: Boolean? = null,
    val tagColor: String? = null,
    val tagStyle: String? = null,
    val textColor: String? = null,
    val borderColor: String? = null,
    val type: String? = null
)

@Serializable
data class BadgeGroupDto(
    val id: String? = null,
    val name: String? = null,
    val color: String? = null
)

/** Runtime view of a badge to render (resolved image + colors). */
data class StreamBadge(
    val name: String,
    val imageUrl: String,
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = ""
) {
    /** Background color when [tagStyle] is "filled" and [tagColor] parses. */
    val backgroundColorOrNull: Color?
        get() = tagColor.toBadgeColorOrNull()?.takeIf { tagStyle.equals("filled", ignoreCase = true) }

    val borderColorOrNull: Color?
        get() = borderColor.toBadgeColorOrNull()

    /** Key used to de-duplicate identical badges across candidate strings. */
    val dedupeKey: String
        get() = imageUrl.ifBlank { name }
}

/** A filter with its pattern pre-compiled once. */
class CompiledBadge(
    val badge: StreamBadge,
    private val regex: Regex,
    private val literalHint: String?
) {
    fun matches(text: String): Boolean {
        val hint = literalHint
        if (hint != null && !text.contains(hint, ignoreCase = true)) return false
        return regex.containsMatchIn(text)
    }
}

/** Badges whose pattern matches [releaseTitle], de-duplicated by image/name. */
fun List<CompiledBadge>.badgesFor(releaseTitle: String): List<StreamBadge> {
    if (isEmpty() || releaseTitle.isBlank()) return emptyList()
    val out = LinkedHashMap<String, StreamBadge>()
    forEach { f ->
        if (f.matches(releaseTitle)) {
            val key = f.badge.dedupeKey
            if (key !in out) out[key] = f.badge
        }
    }
    return out.values.toList()
}

/** Parses "#RRGGBB" / "#AARRGGBB" (with or without leading #) into a [Color]. */
fun String.toBadgeColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    return argb.toLongOrNull(16)?.let { Color(it) }
}
