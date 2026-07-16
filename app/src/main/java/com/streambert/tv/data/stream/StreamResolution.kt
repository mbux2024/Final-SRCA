package com.streambert.tv.data.stream

/** Progress / terminal states surfaced while resolving a playable stream. */
sealed interface StreamResolution {
    data class Progress(val message: String) : StreamResolution
    data class Ready(val url: String, val label: String) : StreamResolution
    data class Failure(val message: String) : StreamResolution
}
