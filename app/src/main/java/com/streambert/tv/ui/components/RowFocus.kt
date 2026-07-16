package com.streambert.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A titled horizontal media row.
 *
 * IMPORTANT: do NOT add `Modifier.focusRestorer()` here. On this project's
 * Compose version (foundation 1.7.x) focusRestorer on a lazy layout crashes the
 * app with `IllegalStateException: "Release should only be called once"` in
 * LazyLayoutPinnableItem.release (via FocusRestorerNode.onDetach) whenever a row
 * is detached/recycled — e.g. switching Home tabs. We rely on Compose TV's
 * default focus handling instead, which is stable. (Revisit only after a Compose
 * upgrade that fixes the focusRestorer detach bug.)
 */
@Composable
fun MediaLazyRow(
    modifier: Modifier = Modifier,
    startPadding: Dp = 48.dp,
    endPadding: Dp = 120.dp,
    itemSpacing: Dp = 14.dp,
    state: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        state = state,
        modifier = modifier,
        contentPadding = PaddingValues(start = startPadding, end = endPadding),
        horizontalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content
    )
}
