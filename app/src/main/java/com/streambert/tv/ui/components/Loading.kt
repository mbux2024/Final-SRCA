package com.streambert.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme

/**
 * Lightweight indeterminate spinner. tv-material3 does not ship a
 * CircularProgressIndicator, so this draws a rotating arc instead.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val sweepStart by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.size(48.dp)) {
        val stroke = Stroke(width = 6f)
        drawArc(
            color = color,
            startAngle = sweepStart,
            sweepAngle = 90f,
            useCenter = false,
            size = Size(size.width, size.height),
            style = stroke
        )
    }
}
