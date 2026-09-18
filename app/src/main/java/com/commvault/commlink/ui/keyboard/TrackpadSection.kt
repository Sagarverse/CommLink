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
import androidx.compose.ui.viewinterop.AndroidView
import android.view.View
import android.view.MotionEvent
import androidx.compose.runtime.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    var isOtgRelayEnabled by remember { mutableStateOf(false) }
    val trackpadSensitivity by viewModel.trackpadSensitivity.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PRECISION TOUCH AREA",
                color = TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            
            Button(
                onClick = { isOtgRelayEnabled = !isOtgRelayEnabled },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isOtgRelayEnabled) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else LightSurfaceAlt
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(28.dp)
            ) {
                Text(
                    text = if (isOtgRelayEnabled) "OTG RELAY: ON" else "OTG RELAY: OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isOtgRelayEnabled) {
            // OTG Mouse Relay Capture View
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(com.commvault.commlink.ui.theme.LocalPrimaryColor.current.copy(alpha = 0.1f))
                    .border(1.dp, com.commvault.commlink.ui.theme.LocalPrimaryColor.current, RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { context ->
                        object : View(context) {
                            init {
                                isFocusable = true
                                isFocusableInTouchMode = true
                                
                                // Handle physical mouse button clicks
                                setOnTouchListener { v, event ->
                                    val buttonState = event.buttonState
                                    val leftClick = (buttonState and MotionEvent.BUTTON_PRIMARY) != 0
                                    val rightClick = (buttonState and MotionEvent.BUTTON_SECONDARY) != 0
                                    val middleClick = (buttonState and MotionEvent.BUTTON_TERTIARY) != 0
                                    
                                    var btnVal = 0
                                    if (leftClick) btnVal = btnVal or 1
                                    if (rightClick) btnVal = btnVal or 2
                                    if (middleClick) btnVal = btnVal or 4
                                    
                                    scope.launch {
                                        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                                            viewModel.sendMouseMove(0f, 0f, buttons = btnVal)
                                        } else if (event.action == MotionEvent.ACTION_UP) {
                                            viewModel.sendMouseMove(0f, 0f, buttons = 0)
                                        }
                                    }
                                    true
                                }
                                
                                // Request pointer capture when attached to window
                                addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                    override fun onViewAttachedToWindow(v: View) {
                                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                                            v.post { v.requestPointerCapture() }
                                        }
                                    }
                                    override fun onViewDetachedFromWindow(v: View) {
                                        if (android.os.Build.VERSION.SDK_INT >= 26) {
                                            v.releasePointerCapture()
                                        }
                                    }
                                })
                            }
                            
                            // THIS is the correct callback for captured pointer events.
                            // When requestPointerCapture() is active, Android delivers
                            // mouse deltas here as ACTION_MOVE with AXIS_X/AXIS_Y
                            // containing relative movement (not absolute coordinates).
                            override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
                                if (event.action == MotionEvent.ACTION_MOVE) {
                                    val dx = event.getX()  // Relative delta X when pointer is captured
                                    val dy = event.getY()  // Relative delta Y when pointer is captured
                                    
                                    val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                    
                                    // Also check button state during move
                                    val buttonState = event.buttonState
                                    var btnVal = 0
                                    if ((buttonState and MotionEvent.BUTTON_PRIMARY) != 0) btnVal = btnVal or 1
                                    if ((buttonState and MotionEvent.BUTTON_SECONDARY) != 0) btnVal = btnVal or 2
                                    if ((buttonState and MotionEvent.BUTTON_TERTIARY) != 0) btnVal = btnVal or 4
                                    
                                    scope.launch {
                                        viewModel.sendMouseMove(
                                            dx * 1.5f, dy * 1.5f, 
                                            buttons = btnVal, 
                                            wheel = if (scrollY != 0f) (scrollY * 10).toInt() else 0
                                        )
                                    }
                                    return true
                                }
                                
                                // Handle button press/release in captured mode
                                if (event.action == MotionEvent.ACTION_BUTTON_PRESS || 
                                    event.action == MotionEvent.ACTION_BUTTON_RELEASE) {
                                    val buttonState = event.buttonState
                                    var btnVal = 0
                                    if ((buttonState and MotionEvent.BUTTON_PRIMARY) != 0) btnVal = btnVal or 1
                                    if ((buttonState and MotionEvent.BUTTON_SECONDARY) != 0) btnVal = btnVal or 2
                                    if ((buttonState and MotionEvent.BUTTON_TERTIARY) != 0) btnVal = btnVal or 4
                                    
                                    scope.launch {
                                        viewModel.sendMouseMove(0f, 0f, buttons = btnVal)
                                    }
                                    return true
                                }
                                
                                return super.onCapturedPointerEvent(event)
                            }
                            
                            // Fallback: if pointer capture isn't granted, handle generic motion
                            override fun onGenericMotionEvent(event: MotionEvent): Boolean {
                                if (event.action == MotionEvent.ACTION_HOVER_MOVE) {
                                    val relX = if (android.os.Build.VERSION.SDK_INT >= 26) event.getAxisValue(MotionEvent.AXIS_RELATIVE_X) else 0f
                                    val relY = if (android.os.Build.VERSION.SDK_INT >= 26) event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y) else 0f
                                    
                                    val dx = if (relX != 0f) relX else event.getAxisValue(MotionEvent.AXIS_X)
                                    val dy = if (relY != 0f) relY else event.getAxisValue(MotionEvent.AXIS_Y)
                                    
                                    scope.launch {
                                        viewModel.sendMouseMove(dx * 1.5f, dy * 1.5f, buttons = 0)
                                    }
                                    return true
                                }
                                if (event.action == MotionEvent.ACTION_SCROLL) {
                                    val scrollY = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                                    scope.launch {
                                        viewModel.sendMouseMove(0f, 0f, buttons = 0, wheel = (scrollY * 10).toInt())
                                    }
                                    return true
                                }
                                return super.onGenericMotionEvent(event)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.TouchApp, 
                        contentDescription = null, 
                        tint = com.commvault.commlink.ui.theme.LocalPrimaryColor.current, 
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "OTG MOUSE CAPTURED", 
                        color = com.commvault.commlink.ui.theme.LocalPrimaryColor.current, 
                        fontSize = 12.sp, 
                        fontWeight = FontWeight.Bold, 
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Move physical mouse to control PC", 
                        color = TextTertiary, 
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
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

                                // Pure 1:1 linear delta movement with flat sensitivity
                                val sensitivity = trackpadSensitivity
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
