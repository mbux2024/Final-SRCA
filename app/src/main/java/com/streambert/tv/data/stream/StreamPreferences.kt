package com.streambert.tv.data.stream

/**
 * Nuvio-style stream feature filters. These decide which of the aggregated
 * source releases are shown/auto-picked, based on tags parsed from the release
 * name (see [ReleaseBadges]). This mirrors NuvioTV's `DebridStreamFeatureFilter`
 * / `DebridStreamCodecFilter` / `DebridStreamMinimumQuality` model:
 *
 *  - Feature filters (Dolby Vision, HDR) are tri-state:
 *      ANY     -> no constraint
 *      EXCLUDE -> drop releases that HAVE the feature
 *      ONLY    -> keep only releases that HAVE the feature
 *  - The codec filter narrows to a single video encode.
 *  - The minimum-quality filter drops anything below a resolution floor.
 *
 * The practical purpose (same as Nuvio): stop the app from auto-selecting a
 * stream a given device can't play well — e.g. exclude Dolby Vision on a box
 * with no DV decoder, or cap at 1080p on a weak device — instead of trying to
 * transcode/strip at playback time.
 */
enum class DynamicRangeFilter {
    ANY,
    EXCLUDE,
    ONLY;

    companion object {
        fun fromName(value: String?): DynamicRangeFilter =
            runCatching { valueOf(value.orEmpty()) }.getOrDefault(ANY)
    }
}

enum class VideoCodecFilter(val label: String) {
    ANY("Any"),
    H264("H.264"),
    HEVC("HEVC"),
    AV1("AV1");

    companion object {
        fun fromName(value: String?): VideoCodecFilter =
            runCatching { valueOf(value.orEmpty()) }.getOrDefault(ANY)
    }
}

enum class MinQualityFilter(val label: String, val minResolution: Int) {
    ANY("Any", 0),
    P720("720p+", 720),
    P1080("1080p+", 1080),
    P2160("4K only", 2160);

    companion object {
        fun fromName(value: String?): MinQualityFilter =
            runCatching { valueOf(value.orEmpty()) }.getOrDefault(ANY)
    }
}

/** Snapshot of the active stream filters, resolved from settings. */
data class StreamFilters(
    val dolbyVision: DynamicRangeFilter = DynamicRangeFilter.ANY,
    val hdr: DynamicRangeFilter = DynamicRangeFilter.ANY,
    val codec: VideoCodecFilter = VideoCodecFilter.ANY,
    val minQuality: MinQualityFilter = MinQualityFilter.ANY
) {
    val isNoOp: Boolean
        get() = dolbyVision == DynamicRangeFilter.ANY &&
            hdr == DynamicRangeFilter.ANY &&
            codec == VideoCodecFilter.ANY &&
            minQuality == MinQualityFilter.ANY

    /**
     * Apply the filters to a list of options. Tag detection reuses the same
     * badges already computed by [ReleaseBadges], so this stays consistent with
     * what the user sees on each source row.
     *
     * Dolby Vision is a subset of "HDR" conceptually, but we treat the two
     * filters independently (as Nuvio does): a DV release carries the DV badge;
     * the HDR filter matches HDR/HDR10/HDR10+/HLG (and DV, since DV is HDR).
     */
    fun applyTo(options: List<StreamOption>): List<StreamOption> {
        if (isNoOp) return options
        return options.filter { opt ->
            val badges = opt.badges
            val hasDv = badges.any { it.equals("DOLBY VISION", true) }
            val hasHdr = hasDv || badges.any {
                it.equals("HDR", true) || it.equals("HDR10+", true) || it.equals("HLG", true)
            }

            val dvOk = when (dolbyVision) {
                DynamicRangeFilter.ANY -> true
                DynamicRangeFilter.EXCLUDE -> !hasDv
                DynamicRangeFilter.ONLY -> hasDv
            }
            val hdrOk = when (hdr) {
                DynamicRangeFilter.ANY -> true
                DynamicRangeFilter.EXCLUDE -> !hasHdr
                DynamicRangeFilter.ONLY -> hasHdr
            }
            val codecOk = when (codec) {
                VideoCodecFilter.ANY -> true
                VideoCodecFilter.H264 -> badges.any { it.equals("H.264", true) }
                VideoCodecFilter.HEVC -> badges.any { it.equals("HEVC", true) }
                VideoCodecFilter.AV1 -> badges.any { it.equals("AV1", true) }
            }
            val qualityOk = opt.quality >= minQuality.minResolution

            dvOk && hdrOk && codecOk && qualityOk
        }
    }
}
