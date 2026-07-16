package com.streambert.tv.ui.components

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import com.streambert.tv.ui.theme.NetflixRed

data class SideNavItem(
    val icon: ImageVector,
    val route: String,
    val contentDescription: String
)

/**
 * Netflix/Prime-style Android TV Side Navigation (48dp icon-only sidebar).
 * Completely passive — only responds to direct focus, never steals it.
 */
@Composable
fun SideNavigation(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToContent: (() -> Unit)? = null
) {
    val navItems = listOf(
        SideNavItem(Icons.Default.Search, "search", "Search"),
        SideNavItem(Icons.Default.Home, "home", "Home"),
        SideNavItem(Icons.Default.Movie, "movies", "Movies"),
        SideNavItem(Icons.Default.Tv, "shows", "TV Shows"),
        SideNavItem(Icons.Default.BookmarkBorder, "my-list", "My List"),
        SideNavItem(Icons.Default.Settings, "settings", "Settings")
    )

    // Each icon is independently focusable — no container focus management
    Column(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = 0.9f)),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        navItems.forEachIndexed { index, item ->
            SideNavIcon(
                item = item,
                isSelected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) },
                onNavigateRight = onNavigateToContent
            )
            if (index < navItems.size - 1) {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Individual Netflix-style navigation icon — completely independent focus.
 */
@Composable
private fun SideNavIcon(
    item: SideNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onNavigateRight: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.5f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "nav_icon_scale"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .scale(scale)
            .background(
                color = when {
                    isSelected -> NetflixRed
                    isFocused -> Color.White.copy(alpha = 0.2f)
                    else -> Color.Transparent
                },
                shape = RoundedCornerShape(6.dp)
            )
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when {
                        keyEvent.key == Key.DirectionRight -> {
                            onNavigateRight?.invoke()
                            true
                        }
                        keyEvent.key == Key.Enter || keyEvent.key == Key.DirectionCenter ||
                            keyEvent.nativeKeyEvent.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp)
        )
    }
}
