package com.commvault.commlink.ui.alarm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("⏰ Interval", "🔊 Escalating")
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = CommvaultPink, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Advanced Alarms", color = CommvaultNavy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(LightBg, CommvaultPinkSoft.copy(alpha = 0.2f))
                    )
                )
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Premium segmented tabs
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = LightSurfaceAlt,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) LightSurface else Color.Transparent,
                            shadowElevation = if (isSelected) 4.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp),
                            onClick = { selectedTab = index }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 12.dp)) {
                                Text(
                                    title,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) CommvaultNavy else TextTertiary,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            when (selectedTab) {
                0 -> IntervalReminderTab(viewModel, context)
                1 -> EscalatingAlarmTab(viewModel, context)
            }
        }
    }
}

@Composable
fun IntervalReminderTab(viewModel: CommLinkViewModel, context: android.content.Context) {
    var title by remember { mutableStateOf("") }
    var intervalStr by remember { mutableStateOf("") }
    var repeatCountStr by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(100); visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(
            initialOffsetY = { 50 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = LightSurface,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("CONFIGURE REMINDER", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Alarm Title") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CommvaultPink,
                        unfocusedBorderColor = BorderColor
                    )
                )

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = intervalStr,
                        onValueChange = { intervalStr = it },
                        label = { Text("Interval (s)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CommvaultPink,
                            unfocusedBorderColor = BorderColor
                        )
                    )

                    OutlinedTextField(
                        value = repeatCountStr,
                        onValueChange = { repeatCountStr = it },
                        label = { Text("Repeats") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CommvaultPink,
                            unfocusedBorderColor = BorderColor
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val interval = intervalStr.toLongOrNull() ?: 10L
                            val count = repeatCountStr.toIntOrNull() ?: 5
                            val t = if (title.isBlank()) "Reminder" else title
                            viewModel.startIntervalAlarm(t, interval, count, context)
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(GradientTealMint),
                                RoundedCornerShape(14.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶  Start", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Button(
                        onClick = { viewModel.stopAlarms() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text("■  Stop", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun EscalatingAlarmTab(viewModel: CommLinkViewModel, context: android.content.Context) {
    var maxVolume by remember { mutableStateOf(5f) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { delay(100); visible = true }

    // Animate the volume icon
    val volumeScale by rememberInfiniteTransition(label = "volPulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "volumeScale"
    )

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(400)) + slideInVertically(
            initialOffsetY = { 50 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = LightSurface,
            shadowElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = null,
                        tint = CommvaultPink,
                        modifier = Modifier.size(28.dp).scale(volumeScale)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Target Volume: ${maxVolume.toInt()}/10", color = CommvaultNavy, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Gradually escalates from silent", color = TextSecondary, fontSize = 12.sp)
                    }
                }

                Slider(
                    value = maxVolume,
                    onValueChange = { maxVolume = it },
                    valueRange = 1f..10f,
                    steps = 8,
                    colors = SliderDefaults.colors(
                        thumbColor = CommvaultPink,
                        activeTrackColor = CommvaultPink,
                        inactiveTrackColor = BorderColor
                    )
                )

                // Volume level indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    (1..10).forEach { level ->
                        Box(
                            modifier = Modifier
                                .size(width = 20.dp, height = (8 + level * 3).dp)
                                .background(
                                    if (level <= maxVolume.toInt()) CommvaultPink.copy(alpha = 0.2f + (level / 15f))
                                    else BorderColor.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CommvaultPinkSoft.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Plays the default alarm tone and increases volume every 3 seconds until it reaches your target level.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.startEscalatingAlarm(maxVolume.toInt(), context)
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(GradientPinkPurple),
                                RoundedCornerShape(14.dp)
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶  Start Alarm", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Button(
                        onClick = { viewModel.stopAlarms() },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                    ) {
                        Text("■  Stop", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
