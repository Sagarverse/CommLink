package com.commvault.commlink.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TrackpadSection(viewModel: CommLinkViewModel) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "PRECISION TOUCH AREA",
            color = TextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Trackpad Surface
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(LightSurfaceAlt)
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            scope.launch {
                                viewModel.sendMouseMove(0f, 0f, buttons = 1)
                                delay(40)
                                viewModel.sendMouseMove(0f, 0f, buttons = 0)
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDoubleTap = {
                            scope.launch {
                                viewModel.sendMouseMove(0f, 0f, buttons = 1)
                                delay(40)
                                viewModel.sendMouseMove(0f, 0f, buttons = 0)
                                delay(40)
                                viewModel.sendMouseMove(0f, 0f, buttons = 1)
                                delay(40)
                                viewModel.sendMouseMove(0f, 0f, buttons = 0)
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        var scrollAccumulator = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            
                            if (pointers.size == 1) {
                                val change = pointers.first()
                                val dragAmount = change.positionChange()
                                if (dragAmount.getDistanceSquared() > 0f) {
                                    viewModel.sendMouseMove(dx = dragAmount.x * 1.5f, dy = dragAmount.y * 1.5f)
                                    change.consume()
                                }
                            } else if (pointers.size == 2) {
                                val change = pointers.first()
                                val dragAmount = change.positionChange()
                                if (dragAmount.y != 0f) {
                                    scrollAccumulator += dragAmount.y
                                    if (Math.abs(scrollAccumulator) > 10f) {
                                        val scrollDelta = (scrollAccumulator / 10f).toInt()
                                        viewModel.sendMouseMove(0f, 0f, 0, -scrollDelta)
                                        scrollAccumulator = 0f
                                    }
                                    pointers.forEach { it.consume() }
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.TouchApp, 
                    contentDescription = null, 
                    tint = TextTertiary.copy(alpha = 0.2f), 
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SWIPE TO MOVE CURSOR", 
                    color = TextTertiary.copy(alpha = 0.4f), 
                    fontSize = 11.sp, 
                    fontWeight = FontWeight.Bold, 
                    letterSpacing = 2.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Mouse Buttons at bottom
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MouseButton(
                modifier = Modifier.weight(1.5f),
                text = "LEFT CLICK",
                onClick = {
                    scope.launch {
                        viewModel.sendMouseMove(0f, 0f, buttons = 1)
                        delay(40)
                        viewModel.sendMouseMove(0f, 0f, buttons = 0)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            )
            MouseButton(
                modifier = Modifier.weight(1f),
                text = "RIGHT",
                onClick = {
                    scope.launch {
                        viewModel.sendMouseMove(0f, 0f, buttons = 2)
                        delay(40)
                        viewModel.sendMouseMove(0f, 0f, buttons = 0)
                    }
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            )
        }
    }
}
