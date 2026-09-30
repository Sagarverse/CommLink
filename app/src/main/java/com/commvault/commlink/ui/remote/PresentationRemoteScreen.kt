package com.commvault.commlink.ui.remote

import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TvOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.material.icons.filled.Mouse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationRemoteScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    val primaryColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current
    
    var showActionToast by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(showActionToast) {
        if (showActionToast != null) {
            delay(1000)
            showActionToast = null
        }
    }
    
    DisposableEffect(Unit) {
        viewModel.setPresentationModeActive(true)
        onDispose {
            viewModel.setPresentationModeActive(false)
        }
    }

    fun vibrate(duration: Long = 30) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(duration)
        }
    }

    var isAirMouseActive by remember { mutableStateOf(false) }

    DisposableEffect(isAirMouseActive) {
        if (isAirMouseActive) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            
            var lastUpdate = 0L
            
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    if (event == null) return
                    val now = System.currentTimeMillis()
                    // Throttle to ~40ms (25fps) to prevent HID buffer overflow
                    if (now - lastUpdate > 40) {
                        lastUpdate = now
                        // Gyroscope values are rad/s.
                        // For landscape/portrait holding, map Z to X and X to Y, or just simple mapping.
                        // Assuming phone held upright: rotation around Y axis (event.values[1]) -> X movement
                        // rotation around X axis (event.values[0]) -> Y movement
                        val dx = -(event.values[1] * 40f) // Sensitivity multiplier
                        val dy = -(event.values[0] * 40f)
                        viewModel.sendMouseMove(dx, dy)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            
            if (gyroSensor != null) {
                sensorManager.registerListener(listener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
            } else {
                showActionToast = "No Gyroscope Found"
                isAirMouseActive = false
            }
            
            onDispose {
                sensorManager.unregisterListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presentation Mode", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        isAirMouseActive = !isAirMouseActive
                        vibrate(40)
                        showActionToast = if (isAirMouseActive) "Air Mouse ON" else "Air Mouse OFF"
                    }) {
                        Icon(Icons.Default.Mouse, contentDescription = "Air Mouse", tint = if (isAirMouseActive) Color.Green else Color.White)
                    }
                    IconButton(onClick = { 
                        viewModel.sendPresentationBlack()
                        vibrate(50)
                        showActionToast = "Black Screen"
                    }) {
                        Icon(Icons.Default.TvOff, contentDescription = "Black Screen", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = primaryColor
                )
            )
        },
        containerColor = com.commvault.commlink.ui.theme.PageBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main gesture area
            Row(modifier = Modifier.fillMaxSize()) {
                // Left 40% - Previous Slide
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.4f)
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    viewModel.sendPresentationPrev()
                                    vibrate(20)
                                    showActionToast = "Previous Slide"
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "PREV",
                        color = primaryColor.copy(alpha = 0.3f),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // Divider
                Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(com.commvault.commlink.ui.theme.BorderColor))

                // Right 60% - Next Slide
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(0.6f)
                        .background(Color.White)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    viewModel.sendPresentationNext()
                                    vibrate(40) // Slightly longer vibration for NEXT to differentiate blindly
                                    showActionToast = "Next Slide"
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "NEXT",
                        color = primaryColor.copy(alpha = 0.5f),
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            // Invisible swipe detector over everything for Start/Stop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        var swipeOffsetY = 0f
                        detectDragGestures(
                            onDragEnd = {
                                if (swipeOffsetY < -200) { // Swipe Up
                                    viewModel.sendPresentationStart()
                                    vibrate(60)
                                    showActionToast = "Start Presentation (F5)"
                                } else if (swipeOffsetY > 200) { // Swipe Down
                                    viewModel.sendPresentationEnd()
                                    vibrate(60)
                                    showActionToast = "End Presentation (Esc)"
                                }
                                swipeOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                swipeOffsetY += dragAmount.y
                            }
                        )
                    }
            )

            // Hints overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .alpha(0.6f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Swipe Up to Start • Swipe Down to Stop", fontSize = 12.sp, color = TextSecondary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap Left side for Prev • Tap Right side for Next", fontSize = 12.sp, color = TextSecondary)
            }
            
            // Toast overlay
            if (showActionToast != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.7f), shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = showActionToast!!,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
