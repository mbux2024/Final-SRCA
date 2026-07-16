package com.streambert.tv

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import com.streambert.tv.ui.navigation.AppNavigation
import com.streambert.tv.ui.theme.StreambertTvTheme

class MainActivity : ComponentActivity() {

    /**
     * Set by [AppNavigation] once the NavController exists. Invoked on a long
     * back-press to jump straight to the Home screen.
     */
    var onNavigateHome: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as StreambertApp).container

        setContent {
            StreambertTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                ) {
                    // Decide the start destination based on whether the user
                    // has finished first-run setup (TMDB key present).
                    val ready by container.settingsRepository.isConfigured
                        .collectAsState(initial = null)

                    AppNavigation(
                        container = container,
                        isConfigured = ready,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                }
            }
        }
    }

    // BACK handling uses Android's standard long-press pattern:
    //   onKeyDown(startTracking) -> onKeyLongPress (hold = Home) / onKeyUp (tap = back).
    // A short tap is routed through OnBackPressedDispatcher so the topmost enabled
    // handler wins — an open overlay's BackHandler closes first, otherwise the
    // NavHost pops exactly one level (and finishes the app at the root). We never
    // manually pop or skip levels, so Back can't jump multiple screens.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.repeatCount == 0) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // Hold Back -> jump straight to Home (clears the back stack).
            onNavigateHome?.invoke()
            return true
        }
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // A long press cancels the up event (event.isCanceled), so a tap only
        // reaches here when it was NOT held long enough to trigger "go Home".
        if (keyCode == KeyEvent.KEYCODE_BACK && event.isTracking && !event.isCanceled) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onKeyUp(keyCode, event)
    }
}
