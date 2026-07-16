package com.streambert.tv.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.streambert.tv.data.settings.SettingsRepository
import com.streambert.tv.ui.components.TvTextField
import kotlinx.coroutines.launch
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * First-run setup. Collects the TMDB read-access token (required) and the
 * TorBox API key (required for playback — TorBox is the default source).
 */
@Composable
fun SetupScreen(
    settings: SettingsRepository,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var tmdb by remember { mutableStateOf("") }
    var torbox by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 96.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = "Welcome to Streambert TV",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Enter your API keys to get started. TMDB powers the catalog. " +
                "TorBox is your debrid account — by default we stream through Torrentio " +
                "using this key. (You can switch to a custom Comet URL later in Settings.)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        TvTextField(
            value = tmdb,
            onValueChange = { tmdb = it },
            label = "TMDB Read Access Token (v4)",
            placeholder = "eyJhbGciOi…",
            modifier = Modifier.width(640.dp),
            imeAction = ImeAction.Next
        )

        TvTextField(
            value = torbox,
            onValueChange = { torbox = it },
            label = "TorBox API Key",
            placeholder = "Your TorBox key",
            modifier = Modifier.width(640.dp),
            imeAction = ImeAction.Done
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }

        Button(onClick = {
            if (tmdb.isBlank()) {
                error = "A TMDB token is required."
                return@Button
            }
            scope.launch {
                settings.setTmdbKey(tmdb)
                settings.setTorboxKey(torbox)
                onDone()
            }
        }) {
            Text("Continue", modifier = Modifier.padding(horizontal = 12.dp))
        }

        Text(
            text = "You can change these any time in Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
