package com.streambert.tv.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.CatalogRow
import com.streambert.tv.ui.components.LoadingIndicator
import com.streambert.tv.ui.components.StandardRow
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Shared UI state for a "browse a catalog" screen (service or genre). */
data class BrowseUiState(
    val loading: Boolean = true,
    val title: String = "",
    val hero: CatalogItem? = null,
    val rows: List<CatalogRow> = emptyList(),
    val error: String? = null
)

/** Reusable screen that renders a titled hero + rows for Service/Genre browsing. */
@Composable
fun CatalogBrowseScreen(
    state: BrowseUiState,
    onSelect: (CatalogItem) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }

            state.error != null && state.rows.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(48.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    state.error!!,
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleMedium
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) { Text("Retry") }
                    Button(onClick = onBack) { Text("Back") }
                }
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                item { Header(state = state, onBack = onBack) }
                items(state.rows, key = { it.title }) { row ->
                    StandardRow(title = row.title, items = row.items, onSelect = onSelect)
                }
            }
        }
    }
}

@Composable
private fun Header(state: BrowseUiState, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(320.dp)
    ) {
        AsyncImage(
            model = state.hero?.backdropUrl ?: state.hero?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xB30B0B0F),
                        0.5f to Color(0x660B0B0F),
                        1f to Color(0xF20B0B0F)
                    )
                )
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(20.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
        }
        Text(
            state.title,
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 20.dp)
        )
    }
}
