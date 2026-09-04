package com.commvault.commlink.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sqrt

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

        // Trackpad Surface — single unified pointer handler
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(LightSurfaceAlt)
                .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        // Accumulators for two-finger scroll
                        var scrollAccumulator = 0f
                        // For tap detection
                        var lastTapTimeMs = 0L

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            val allPointers = event.changes

                            // --- Detect finger lift for tap ---
                            val justReleasedPointers = allPointers.filter { !it.pressed && it.previousPressed }
                            if (justReleasedPointers.size == 1 && pointers.isEmpty()) {
                                val released = justReleasedPointers.first()
                                val pressDurationMs = released.uptimeMillis - released.previousUptimeMillis
                                // Total displacement during this pointer's life
                                val totalDx = released.position.x - released.previousPosition.x
                                val totalDy = released.position.y - released.previousPosition.y
                                val totalDist = sqrt(totalDx * totalDx + totalDy * totalDy)

                                // Tap: short press (<250ms) and minimal movement (<12px)
                                if (pressDurationMs < 250 && totalDist < 12f) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTapTimeMs < 350) {
                                        // Double-tap
                                        scope.launch {
                                            viewModel.sendMouseMove(0f, 0f, buttons = 1)
                                            delay(40)
                                            viewModel.sendMouseMove(0f, 0f, buttons = 0)
                                            delay(40)
                                            viewModel.sendMouseMove(0f, 0f, buttons = 1)
                                            delay(40)
                                            viewModel.sendMouseMove(0f, 0f, buttons = 0)
                                        }
                                        lastTapTimeMs = 0L // Reset to prevent triple-tap
                                    } else {
                                        // Single tap
                                        lastTapTimeMs = now
                                        scope.launch {
                                            viewModel.sendMouseMove(0f, 0f, buttons = 1)
                                            delay(40)
                                            viewModel.sendMouseMove(0f, 0f, buttons = 0)
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    allPointers.forEach { it.consume() }
                                    continue
                                }
                            }

                            // --- Single finger drag (cursor movement) ---
                            if (pointers.size == 1) {
                                val change = pointers.first()
                                val dragAmount = change.positionChange()

                                // Pure 1:1 linear delta movement with flat sensitivity (no deadzone jitter/jumping)
                                val sensitivity = 1.8f
                                viewModel.sendMouseMove(
                                    dx = dragAmount.x * sensitivity,
                                    dy = dragAmount.y * sensitivity
                                )
                                change.consume()
                            }
                            // --- Two-finger scroll ---
                            else if (pointers.size == 2) {
                                val change = pointers.first()
                                val dragAmount = change.positionChange()
                                if (dragAmount.y != 0f) {
                                    scrollAccumulator += dragAmount.y
                                    // Smooth scroll: lower accumulator threshold
                                    if (abs(scrollAccumulator) > 5f) {
                                        val scrollDelta = (scrollAccumulator / 5f).toInt()
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
