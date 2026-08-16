package com.commvault.commlink.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue

fun Modifier.shimmerEffect(): Modifier = composed {
    var size = androidx.compose.ui.geometry.Size.Zero
    val transition = rememberInfiniteTransition()
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.let { if (it == 0f) 1000f else it },
        targetValue = 2 * size.width.let { if (it == 0f) 1000f else it },
        animationSpec = infiniteRepeatable(
            animation = tween(1000)
        )
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFFB8BFCE).copy(alpha = 0.2f),
                Color(0xFFB8BFCE).copy(alpha = 0.6f),
                Color(0xFFB8BFCE).copy(alpha = 0.2f),
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.let { if (it == 0f) 1000f else it }, size.height.let { if (it == 0f) 1000f else it })
        )
    )
}
