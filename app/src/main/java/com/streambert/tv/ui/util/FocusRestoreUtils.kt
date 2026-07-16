package com.streambert.tv.ui.util

import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester

/**
 * Requests focus after a few composition frames have elapsed, retrying if the
 * target isn't attached yet. Mirrors NuvioTV's focus-restore behaviour so the
 * first card reliably grabs focus on entry (Compose can drop an immediate
 * requestFocus() call before the node is laid out).
 */
suspend fun FocusRequester.requestFocusAfterFrames(frames: Int = 3) {
    repeat(frames.coerceAtLeast(1)) {
        withFrameNanos { }
    }
    repeat(5) {
        val ok = runCatching { requestFocus() }.isSuccess
        if (ok) return
        withFrameNanos { }
    }
}
