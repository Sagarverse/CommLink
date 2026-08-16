package com.commvault.commlink.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.commvault.commlink.ui.theme.*

// ─── Bounce-on-press with lift shadow ─────────────────────────────────
@Composable
fun Modifier.bounceClick(
    enabled: Boolean = true,
    scaleDown: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "BounceClickScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "BounceElevation"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            shadowElevation = elevation.toPx()
            shape = RoundedCornerShape(16.dp)
            clip = false
        }
        .pointerInput(enabled) {
            if (enabled) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        isPressed = true
                        waitForUpOrCancellation()
                        isPressed = false
                    }
                }
            }
        }
        .clickable(
            enabled = enabled,
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
}

// ─── Staggered fade-in entrance animation ─────────────────────────────
@Composable
fun Modifier.staggeredFadeIn(
    index: Int,
    baseDelay: Int = 50
): Modifier = composed {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay((index * baseDelay).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "staggerAlpha"
    )
    val offsetY by animateFloatAsState(
        targetValue = if (visible) 0f else 24f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "staggerOffset"
    )
    this.graphicsLayer {
        this.alpha = alpha
        translationY = offsetY
    }
}

// Note: shimmerEffect is defined in Shimmer.kt


// ─── Gradient accent border glow ──────────────────────────────────────
@Composable
fun Modifier.glowBorder(
    glowColor: Color = CommvaultPink,
    cornerRadius: Dp = 16.dp,
    glowAlpha: Float = 0.3f
): Modifier = composed {
    this.drawBehind {
        drawRoundRect(
            color = glowColor.copy(alpha = glowAlpha),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            size = Size(size.width + 4.dp.toPx(), size.height + 4.dp.toPx()),
            topLeft = Offset(-2.dp.toPx(), -2.dp.toPx())
        )
    }
}
