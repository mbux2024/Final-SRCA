package com.streambert.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.streambert.tv.data.model.CatalogItem
import com.streambert.tv.data.model.MediaType
import com.streambert.tv.ui.util.requestFocusAfterFrames
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text

/**
 * NuvioTV-style quick options menu shown when a poster is long-pressed
 * (hold OK/Center, or press MENU). Netflix-flavoured styling.
 */
@Composable
fun MediaOptionsDialog(
    item: CatalogItem,
    isInMyList: Boolean,
    onViewDetails: () -> Unit,
    onToggleLibrary: () -> Unit,
    onMarkWatched: () -> Unit,
    onMarkUnwatched: () -> Unit,
    onDismiss: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocusAfterFrames() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xCC000000)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                colors = SurfaceDefaults.colors(containerColor = Color(0xFF16161C)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(480.dp)
            ) {
                Column(Modifier.padding(24.dp)) {
                    // Header: poster thumbnail + title/meta.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(72.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF26262E))
                        ) {
                            AsyncImage(
                                model = item.posterUrl ?: item.backdropUrl,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                item.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            val typeLabel = if (item.type == MediaType.TV) "Series" else "Movie"
                            val meta = listOfNotNull(
                                typeLabel,
                                item.year?.toString(),
                                item.rating.takeIf { it > 0 }?.let { "\u2605 ${"%.1f".format(it)}" }
                            ).joinToString("  \u00b7  ")
                            Text(
                                meta,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0B0B8),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Column(
                        Modifier
                            .padding(top = 20.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OptionRow(
                            icon = Icons.Filled.Info,
                            label = "View Details",
                            modifier = Modifier.focusRequester(firstFocus),
                            onClick = { onDismiss(); onViewDetails() }
                        )
                        OptionRow(
                            icon = if (isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                            label = if (isInMyList) "Remove from Library" else "Add to Library",
                            onClick = { onDismiss(); onToggleLibrary() }
                        )
                        OptionRow(
                            icon = Icons.Filled.Visibility,
                            label = "Mark Watched",
                            onClick = { onDismiss(); onMarkWatched() }
                        )
                        OptionRow(
                            icon = Icons.Filled.VisibilityOff,
                            label = "Mark Unwatched",
                            onClick = { onDismiss(); onMarkUnwatched() }
                        )
                        OptionRow(
                            icon = Icons.Filled.Close,
                            label = "Cancel",
                            onClick = onDismiss
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = androidx.tv.material3.ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = androidx.tv.material3.ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF26262E),
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        scale = androidx.tv.material3.ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = androidx.tv.material3.ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 14.dp)
            )
        }
    }
}
