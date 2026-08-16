package com.commvault.commlink.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val packageName = context.packageName

    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(packageName))
    }
    
    val isAutoConnectEnabled by viewModel.isAutoConnectEnabled.collectAsState()
    val typingSpeed by viewModel.typingSpeed.collectAsState()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsState()

    // Refresh battery optimization status when the screen is recomposed/resumed
    LaunchedEffect(Unit) {
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBg)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            // Connection Settings Group
            SettingsGroupTitle("CONNECTION")
            
            SettingsToggleRow(
                title = "Auto Connect",
                subtitle = "Automatically connect to the last saved workstation on launch.",
                icon = Icons.Default.BluetoothConnected,
                checked = isAutoConnectEnabled,
                onCheckedChange = { viewModel.setAutoConnectEnabled(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Security Group
            SettingsGroupTitle("SECURITY")
            
            SettingsToggleRow(
                title = "Fingerprint Unlock",
                subtitle = "Require biometric authentication to open the app.",
                icon = Icons.Default.Fingerprint,
                checked = isBiometricEnabled,
                onCheckedChange = { viewModel.setBiometricEnabled(it) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // System Settings Group
            SettingsGroupTitle("SYSTEM & PERFORMANCE")

            SettingsSliderRow(
                title = "Macro Typing Speed",
                subtitle = "Adjust the delay between keystrokes.",
                icon = Icons.Default.Keyboard,
                value = typingSpeed.toFloat(),
                valueRange = 10f..200f,
                steps = 18,
                onValueChange = { viewModel.setTypingSpeed(it.toLong()) },
                valueLabel = "${typingSpeed}ms"
            )

            Spacer(modifier = Modifier.height(16.dp))
            
            SettingsActionRow(
                title = "Battery Optimization",
                subtitle = if (isIgnoringBatteryOptimizations) "Unrestricted (Recommended)" else "Restricted (May cause disconnects)",
                icon = if (isIgnoringBatteryOptimizations) Icons.Default.BatteryStd else Icons.Default.BatteryAlert,
                iconTint = if (isIgnoringBatteryOptimizations) SuccessTeal else CommvaultPink,
                onClick = {
                    if (!isIgnoringBatteryOptimizations) {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    } else {
                        // If they are already ignoring, open the app details so they can revoke it if they really want to.
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    }
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Logout Button
            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CommvaultPink.copy(alpha = 0.1f),
                    contentColor = CommvaultPink
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log Out")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Log Out", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        color = TextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
    )
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onCheckedChange(!checked) },
        color = LightSurface,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(LightSurfaceAlt, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = SuccessTeal,
                    uncheckedThumbColor = TextTertiary,
                    uncheckedTrackColor = LightSurfaceAlt
                )
            )
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        color = LightSurface,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(LightSurfaceAlt, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

@Composable
fun SettingsSliderRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    valueLabel: String
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = LightSurface,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, BorderColor.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(LightSurfaceAlt, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                Text(
                    text = valueLabel,
                    color = CommvaultPink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                colors = SliderDefaults.colors(
                    thumbColor = CommvaultPink,
                    activeTrackColor = CommvaultPink,
                    inactiveTrackColor = LightSurfaceAlt
                )
            )
        }
    }
}
