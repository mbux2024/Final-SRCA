package com.streambert.tv.data.tmdb

import androidx.annotation.DrawableRes
import com.streambert.tv.R

/**
 * A streaming service shown in the "Services" row. [providerId] is the TMDB
 * watch-provider id used to discover that service's catalog; [brandColor] is
 * the card background shown behind the bundled [logoRes] artwork.
 */
data class StreamingService(
    val providerId: Int,
    val name: String,
    val brandColor: Long,
    @DrawableRes val logoRes: Int
)

object StreamingServices {
    // Artwork bundled from the user's Media-Data "Services 2" set.
    val ALL = listOf(
        StreamingService(WatchProviders.NETFLIX, "Netflix", 0xFF5A0A0A, R.drawable.svc_netflix),
        StreamingService(WatchProviders.DISNEY_PLUS, "Disney+", 0xFF10345C, R.drawable.svc_disney_plus),
        StreamingService(WatchProviders.APPLE_TV_PLUS, "Apple TV+", 0xFF1A1A1A, R.drawable.svc_apple_tv),
        StreamingService(WatchProviders.PRIME_VIDEO, "Prime Video", 0xFF0F79C7, R.drawable.svc_prime_video),
        StreamingService(WatchProviders.AMC_PLUS, "AMC+", 0xFF1A1A1A, R.drawable.svc_amc_plus),
        StreamingService(WatchProviders.HULU, "Hulu", 0xFF0E3B24, R.drawable.svc_hulu),
        StreamingService(WatchProviders.PARAMOUNT_PLUS, "Paramount+", 0xFF0047AB, R.drawable.svc_paramount),
        StreamingService(WatchProviders.PEACOCK, "Peacock", 0xFF1A1A1A, R.drawable.svc_peacock),
        StreamingService(WatchProviders.STARZ, "Starz", 0xFF141414, R.drawable.svc_starz),
        StreamingService(WatchProviders.DISCOVERY_PLUS, "Discovery+", 0xFF004E92, R.drawable.svc_discovery_plus)
    )
}
