package com.streambert.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.streambert.tv.ui.util.isSelectKey
import com.streambert.tv.ui.util.rememberLongPressKeyTracker

/**
 * Adds NuvioTV-style long-press handling to a focusable card: holding the
 * D-pad OK/Center (or pressing MENU) fires [onLongPress] to open the options
 * menu, while a normal short click still triggers the card's onClick. A no-op
 * when [onLongPress] is null.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.cardLongPress(onLongPress: (() -> Unit)?): Modifier {
    if (onLongPress == null) return this
    val tracker = rememberLongPressKeyTracker()
    val triggered = remember { mutableStateOf(false) }
    return this.onPreviewKeyEvent { keyEvent ->
        val native = keyEvent.nativeKeyEvent
        if (native.action == AndroidKeyEvent.ACTION_DOWN &&
            native.keyCode == AndroidKeyEvent.KEYCODE_MENU
        ) {
            triggered.value = true
            onLongPress()
            return@onPreviewKeyEvent true
        }
        if (tracker.handle(native, ::isSelectKey) {
                triggered.value = true
                onLongPress()
            }
        ) {
            if (native.action == AndroidKeyEvent.ACTION_UP) triggered.value = false
            return@onPreviewKeyEvent true
        }
        if (native.action == AndroidKeyEvent.ACTION_UP &&
            triggered.value &&
            (isSelectKey(native.keyCode) || native.keyCode == AndroidKeyEvent.KEYCODE_MENU)
        ) {
            triggered.value = false
            return@onPreviewKeyEvent true
        }
        false
    }
}
