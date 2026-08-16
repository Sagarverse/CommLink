package com.commvault.commlink.ui.keyboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.theme.*

@Composable
fun PremiumKey(label: String, modifier: Modifier, accent: Color, onPress: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) KeyPressed else KeyBackground)
            .border(
                1.dp,
                if (isPressed) accent.copy(alpha = 0.6f) else BorderColor,
                RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onPress() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) accent else TextPrimary.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.W600
        )
    }
}

@Composable
fun MouseButton(modifier: Modifier, text: String, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "mouseButtonScale")

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LightSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
