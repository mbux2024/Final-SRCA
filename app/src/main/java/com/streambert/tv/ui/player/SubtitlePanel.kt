package com.streambert.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** Nuvio-style subtitle style controls, shared by both player engines. */
data class SubtitleStyle(
    val delayMs: Long = 0L,
    val fontPercent: Int = 100,
    val bold: Boolean = false,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val opacityPercent: Int = 100,
    val outline: Boolean = true
)

/** A selectable subtitle in the middle column. */
data class SubtitleEntry(
    val key: String,
    val source: String,      // "Built in" | "SubSense" | …
    val language: String,    // display language, e.g. "English"
    val subId: String,       // e.g. "subsense-srt-opensubtitles-eng-1"
    val selected: Boolean,
    val onSelect: () -> Unit
)

/** A quick language toggle in the left column ("None" + each available language). */
data class SubtitleLanguage(
    val label: String,
    val count: Int,          // 0 hides the count pill (e.g. for "None")
    val selected: Boolean,
    val onSelect: () -> Unit
)

/** The six Netflix/Nuvio-style subtitle text colors. */
val SUBTITLE_COLORS: List<Int> = listOf(
    0xFFFFFFFF.toInt(), // white
    0xFFB8B8B8.toInt(), // light gray
    0xFFF5C518.toInt(), // yellow
    0xFF35C5F0.toInt(), // cyan
    0xFFF0554E.toInt(), // red
    0xFF39D98A.toInt()  // green
)

/**
 * Full-screen subtitle panel matching NuvioTV: three columns —
 * Languages · Subtitles · Subtitle Style — with live style controls.
 */
@Composable
fun SubtitleStylePanel(
    languages: List<SubtitleLanguage>,
    subtitles: List<SubtitleEntry>,
    style: SubtitleStyle,
    onStyleChange: (SubtitleStyle) -> Unit,
    supportsDelay: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xF20A0A0F))
            .padding(start = 48.dp, top = 36.dp, end = 48.dp, bottom = 24.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Subtitles",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 20.dp)
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                // ── Column 1: Languages ─────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(0.24f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { ColumnHeader("Languages") }
                    itemsIndexed(languages, key = { _, l -> "lang_${l.label}" }) { index, lang ->
                        LanguageRow(
                            lang = lang,
                            modifier = if (index == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }

                // ── Column 2: Subtitles ─────────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item { ColumnHeader("Subtitles") }
                    if (subtitles.isEmpty()) {
                        item {
                            Text(
                                "No subtitles found for this title.",
                                color = Color(0xFF9A9AA2),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    items(subtitles, key = { it.key }) { entry -> SubtitleRow(entry) }
                }

                // ── Column 3: Subtitle Style ────────────────────────────────
                LazyColumn(
                    modifier = Modifier.weight(0.34f).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item { ColumnHeader("Subtitle Style") }
                    item {
                        StepperRow(
                            label = "Delay" + if (!supportsDelay) "  (MPV engine)" else "",
                            valueText = "${style.delayMs}ms",
                            onMinus = { onStyleChange(style.copy(delayMs = style.delayMs - 100)) },
                            onPlus = { onStyleChange(style.copy(delayMs = style.delayMs + 100)) }
                        )
                    }
                    item {
                        StepperRow(
                            label = "Font Size",
                            valueText = "${style.fontPercent}%",
                            onMinus = { onStyleChange(style.copy(fontPercent = (style.fontPercent - 10).coerceIn(50, 250))) },
                            onPlus = { onStyleChange(style.copy(fontPercent = (style.fontPercent + 10).coerceIn(50, 250))) }
                        )
                    }
                    item {
                        ToggleRow(
                            label = "Bold",
                            on = style.bold,
                            onToggle = { onStyleChange(style.copy(bold = !style.bold)) }
                        )
                    }
                    item {
                        Column {
                            StyleLabel("Text Color")
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SUBTITLE_COLORS.forEach { c ->
                                    ColorSwatch(
                                        color = c,
                                        selected = c == style.textColor,
                                        onClick = { onStyleChange(style.copy(textColor = c)) }
                                    )
                                }
                            }
                        }
                    }
                    item {
                        StepperRow(
                            label = "Text Opacity",
                            valueText = "${style.opacityPercent}%",
                            onMinus = { onStyleChange(style.copy(opacityPercent = (style.opacityPercent - 10).coerceIn(0, 100))) },
                            onPlus = { onStyleChange(style.copy(opacityPercent = (style.opacityPercent + 10).coerceIn(0, 100))) }
                        )
                    }
                    item {
                        ToggleRow(
                            label = "Outline",
                            on = style.outline,
                            onToggle = { onStyleChange(style.copy(outline = !style.outline)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = Color(0xFF9A9AA2),
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}

@Composable
private fun StyleLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun LanguageRow(lang: SubtitleLanguage, modifier: Modifier = Modifier) {
    Card(
        onClick = lang.onSelect,
        scale = CardDefaults.scale(focusedScale = 1.03f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(
            containerColor = if (lang.selected) Color.White else Color(0x14FFFFFF),
            focusedContainerColor = if (lang.selected) Color.White else Color(0x33FFFFFF)
        ),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                lang.label,
                color = if (lang.selected) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (lang.count > 0) {
                Text(
                    lang.count.toString(),
                    color = if (lang.selected) Color(0xFF555555) else Color(0xFF9A9AA2),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun SubtitleRow(entry: SubtitleEntry) {
    Card(
        onClick = entry.onSelect,
        scale = CardDefaults.scale(focusedScale = 1.02f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = CardDefaults.colors(
            containerColor = Color(0x14FFFFFF),
            focusedContainerColor = Color(0x2EFFFFFF)
        ),
        border = CardDefaults.border(
            border = if (entry.selected) Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp)) else Border.None,
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                SourceBadge(entry.source)
                Text(
                    entry.language,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (entry.subId.isNotBlank()) {
                    Text(
                        entry.subId,
                        color = Color(0xFF9A9AA2),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (entry.selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    Box(
        Modifier
            .background(Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            source,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StepperRow(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column {
        StyleLabel(label)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StepperButton(icon = Icons.Filled.Remove, desc = "Decrease $label", onClick = onMinus)
            Box(
                Modifier
                    .width(96.dp)
                    .background(Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(valueText, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            StepperButton(icon = Icons.Filled.Add, desc = "Increase $label", onClick = onPlus)
        }
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.08f),
        shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = CardDefaults.colors(containerColor = Color(0x24FFFFFF), focusedContainerColor = Color.White),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
        )
    ) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = desc, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit) {
    Column {
        StyleLabel(label)
        Card(
            onClick = onToggle,
            scale = CardDefaults.scale(focusedScale = 1.04f),
            shape = CardDefaults.shape(shape = RoundedCornerShape(8.dp)),
            colors = CardDefaults.colors(
                containerColor = if (on) Color.White else Color(0x1FFFFFFF),
                focusedContainerColor = if (on) Color.White else Color(0x33FFFFFF)
            ),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(8.dp))
            )
        ) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text(
                    if (on) "On" else "Off",
                    color = if (on) Color.Black else Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        scale = CardDefaults.scale(focusedScale = 1.15f),
        shape = CardDefaults.shape(shape = CircleShape),
        colors = CardDefaults.colors(containerColor = Color(color), focusedContainerColor = Color(color)),
        border = CardDefaults.border(
            border = if (selected) Border(BorderStroke(3.dp, Color.White), shape = CircleShape) else Border.None,
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape)
        )
    ) {
        Box(
            Modifier
                .size(40.dp)
                .border(1.dp, Color(0x55000000), CircleShape)
        )
    }
}
