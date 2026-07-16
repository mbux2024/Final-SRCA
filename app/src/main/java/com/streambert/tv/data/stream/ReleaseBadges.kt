package com.streambert.tv.data.stream

import java.util.Locale

/**
 * Extracts human-readable quality badges (resolution, dynamic range, video
 * codec, audio codec/channels, source, subtitle hint) from a scene/torrent
 * release title. Patterns are our own; used to annotate each source in the
 * "pick a source / quality" panel.
 */
object ReleaseBadges {

    fun parse(title: String): List<String> {
        val t = " ${title.lowercase(Locale.ROOT)} "
        val out = LinkedHashSet<String>()

        // Resolution
        when {
            t.contains("2160") || t.contains("4k") || t.contains("uhd") -> out.add("4K")
            t.contains("1080") -> out.add("1080p")
            t.contains("720") -> out.add("720p")
            t.contains("480") -> out.add("480p")
        }

        // Dynamic range / HDR
        if (Regex("dolby.?vision|\\bdovi\\b|\\bdv\\b|dvhe|dvh1").containsMatchIn(t)) out.add("DOLBY VISION")
        if (t.contains("hdr10+") || t.contains("hdr10plus")) out.add("HDR10+")
        else if (t.contains("hdr")) out.add("HDR")

        // Source
        when {
            t.contains("remux") -> out.add("REMUX")
            Regex("blu.?ray|\\bbdrip\\b|\\bbrrip\\b|\\bbdmv\\b").containsMatchIn(t) -> out.add("BluRay")
            Regex("web.?dl|\\bwebdl\\b").containsMatchIn(t) -> out.add("WEB-DL")
            t.contains("webrip") -> out.add("WEBRip")
            t.contains("hdtv") -> out.add("HDTV")
        }

        // Video codec
        when {
            Regex("x265|h\\.?265|hevc").containsMatchIn(t) -> out.add("HEVC")
            t.contains("av1") -> out.add("AV1")
            Regex("x264|h\\.?264|\\bavc\\b").containsMatchIn(t) -> out.add("H.264")
        }

        // Audio codec (most premium first)
        when {
            t.contains("atmos") -> out.add("ATMOS")
            t.contains("truehd") || t.contains("true-hd") -> out.add("TrueHD")
            Regex("dts.?x|dts:x").containsMatchIn(t) -> out.add("DTS:X")
            Regex("dts.?hd").containsMatchIn(t) -> out.add("DTS-HD")
            Regex("\\bdts\\b").containsMatchIn(t) -> out.add("DTS")
            Regex("e-?ac-?3|\\bddp\\b|dd\\+|eac3").containsMatchIn(t) -> out.add("DD+")
            Regex("\\bac-?3\\b|\\bdd\\b").containsMatchIn(t) -> out.add("DD")
            t.contains("aac") -> out.add("AAC")
        }

        // Channels
        when {
            t.contains("7.1") -> out.add("7.1")
            t.contains("5.1") -> out.add("5.1")
            t.contains("2.0") || t.contains("2ch") -> out.add("2.0")
        }

        // Subtitles / multi-language hint
        if (Regex("multi.?sub|\\bsubs?\\b|vostfr|\\bmulti\\b").containsMatchIn(t)) out.add("SUBS")

        return out.toList()
    }
}
