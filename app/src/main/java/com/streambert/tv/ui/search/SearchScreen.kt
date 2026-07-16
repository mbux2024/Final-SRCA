package com.streambert.tv.ui.search

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.ui.components.LoadingIndicator
import com.streambert.tv.ui.components.MediaCard
import com.streambert.tv.ui.components.TvTextField
import com.streambert.tv.ui.util.requestFocusAfterFrames

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onSelect: (CatalogItem) -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // System speech recognizer (Google) — returns spoken text; no RECORD_AUDIO
    // permission needed since the recognizer app owns the mic.
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.onVoiceQuery(spoken)
    }
    val startVoice: () -> Unit = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a title or describe what to watch")
        }
        runCatching { voiceLauncher.launch(intent) }
    }

    val micFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { micFocus.requestFocusAfterFrames() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        // Top row: discover / voice / search field.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SquareIconButton(Icons.Filled.Explore, "Browse", onClick = onBack)
            SquareIconButton(Icons.Filled.Mic, "Voice search", focusRequester = micFocus, onClick = startVoice)
            TvTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = "",
                placeholder = "Search movies & series",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Search,
                onImeAction = viewModel::onSubmit
            )
        }

        Box(Modifier.fillMaxSize().padding(top = 24.dp)) {
            when {
                // Empty query -> Recent searches (Netflix/Nuvio-style history).
                state.query.isBlank() -> RecentSearches(
                    recent = state.recent,
                    onSelect = viewModel::onRecentSelected,
                    onClear = viewModel::clearHistory
                )

                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }

                state.searched && state.results.isEmpty() -> Text(
                    if (state.aiActive)
                        "No AI matches for \u201c${state.query}\u201d. Try rephrasing, or check your Gemini key in Settings."
                    else
                        "No results for \u201c${state.query}\u201d.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )

                else -> Column(Modifier.fillMaxSize()) {
                    state.header?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.results, key = { "${it.type}_${it.id}" }) { item ->
                            MediaCard(
                                item = item,
                                onClick = {
                                    viewModel.recordCurrentQuery()
                                    onSelect(item)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    onSelect: (String) -> Unit,
    onClear: () -> Unit
) {
    if (recent.isEmpty()) {
        Text(
            "Search for a movie or series, or tap the mic to speak. Your recent searches will show up here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Recent searches",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            ClearHistoryButton(onClear)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(recent, key = { _, term -> term }) { _, term ->
                RecentSearchRow(term = term, onClick = { onSelect(term) })
            }
        }
    }
}

@Composable
private fun RecentSearchRow(term: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.01f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1C1C20),
            focusedContainerColor = Color(0xFF33333B)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            term,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun ClearHistoryButton(onClear: () -> Unit) {
    Card(
        onClick = onClear,
        scale = CardDefaults.scale(focusedScale = 1.04f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF26262C),
            focusedContainerColor = Color(0xFF3A3A44)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
        )
    ) {
        Text(
            "Clear history",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SquareIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.06f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0xFF1B1B1F),
            focusedContainerColor = Color(0xFF33333B)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(12.dp))
        ),
        modifier = modifier.then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}
