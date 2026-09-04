package com.commvault.commlink.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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

    // MAC Spoofing states
    val currentMac by viewModel.currentBluetoothMac.collectAsState()
    val isMacSpoofed by viewModel.isMacSpoofed.collectAsState()
    val macSpoofStatus by viewModel.macSpoofStatus.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()

    // Smart Features states
    val isShakeToLaunchEnabled by viewModel.isShakeToLaunchEnabled.collectAsState()
    val isAutoLockEnabled by viewModel.isAutoLockEnabled.collectAsState()
    val currentSignalStrength by viewModel.currentSignalStrength.collectAsState()
    val autoLockDistanceThreshold by viewModel.autoLockDistanceThreshold.collectAsState()

    // Read MAC on screen open
    LaunchedEffect(Unit) {
        isIgnoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(packageName)
        viewModel.readCurrentBluetoothMac()
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
            // Appearance Section - Theme Color Picker
            SettingsGroupTitle("APPEARANCE")
            
            val themeColorHex by viewModel.themeColorHex.collectAsState()
            val currentPrimary = LocalPrimaryColor.current
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Palette,
                            contentDescription = null,
                            tint = currentPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Theme Color",
                            fontWeight = FontWeight.Bold,
                            color = CommvaultNavy,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Choose a primary color for the entire app",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 30.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Color swatches grid - 5 per row
                    val palette = com.commvault.commlink.ui.theme.ThemeColorPalette
                    for (rowStart in palette.indices step 5) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (i in rowStart until minOf(rowStart + 5, palette.size)) {
                                val option = palette[i]
                                val isSelected = com.commvault.commlink.ui.theme.colorToHex(currentPrimary).equals(
                                    String.format("%06X", option.hex.toInt() and 0xFFFFFF), ignoreCase = true
                                )
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(option.color, CircleShape)
                                        .then(
                                            if (isSelected) Modifier.border(
                                                3.dp,
                                                Color.White,
                                                CircleShape
                                            ) else Modifier
                                        )
                                        .then(
                                            if (isSelected) Modifier.border(
                                                4.dp,
                                                option.color,
                                                CircleShape
                                            ) else Modifier
                                        )
                                        .clickable {
                                            viewModel.setThemeColor(
                                                String.format("#%06X", option.hex.toInt() and 0xFFFFFF)
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (rowStart + 5 < palette.size) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Connection Settings Group
            SettingsGroupTitle("CONNECTION")
            
            SettingsToggleRow(
                title = "Auto Connect",
                subtitle = "Automatically connect to the last saved workstation on launch.",
                icon = Icons.Default.BluetoothConnected,
                checked = isAutoConnectEnabled,
                onCheckedChange = { viewModel.setAutoConnectEnabled(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bluetooth MAC Address Spoofing Card
            BluetoothMacSpoofCard(
                currentMac = currentMac,
                isSpoofed = isMacSpoofed,
                statusMessage = macSpoofStatus,
                savedDevices = savedDevices,
                onApplyMac = { mac -> viewModel.spoofBluetoothMac(mac) },
                onRestoreMac = { viewModel.restoreOriginalMac() }
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
            
            SettingsActionRow(
                title = "Battery Optimization",
                subtitle = if (isIgnoringBatteryOptimizations) "Unrestricted (Recommended)" else "Restricted (May cause disconnects)",
                icon = if (isIgnoringBatteryOptimizations) Icons.Default.BatteryStd else Icons.Default.BatteryAlert,
                iconTint = if (isIgnoringBatteryOptimizations) SuccessTeal else LocalPrimaryColor.current,
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

            Spacer(modifier = Modifier.height(24.dp))

            // Smart Features Group
            SettingsGroupTitle("SMART FEATURES")

            SettingsToggleRow(
                title = "Shake to Launch",
                subtitle = "Shake your phone to bring CommLink to the foreground.",
                icon = Icons.Default.PhoneAndroid,
                checked = isShakeToLaunchEnabled,
                onCheckedChange = { viewModel.setShakeToLaunchEnabled(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingsToggleRow(
                title = "Auto-Lock on Walk Away",
                subtitle = "Lock your PC (Win+L) when Bluetooth signal drops.",
                icon = Icons.Default.Lock,
                checked = isAutoLockEnabled,
                onCheckedChange = { viewModel.setAutoLockEnabled(it) }
            )
            
            AnimatedVisibility(visible = isAutoLockEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(LightSurface, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Signal Strength", color = TextSecondary, fontSize = 14.sp)
                        Text("$currentSignalStrength%", color = if (currentSignalStrength > 50) SuccessTeal else if (currentSignalStrength > 20) WarningAmber else LocalPrimaryColor.current, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { currentSignalStrength / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (currentSignalStrength > 50) SuccessTeal else if (currentSignalStrength > 20) WarningAmber else LocalPrimaryColor.current,
                        trackColor = LightSurfaceAlt,
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val thresholdPercentage = 80 - (autoLockDistanceThreshold * 60).toInt()
                    
                    Text("Lock Distance Threshold", color = TextSecondary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Locks when signal drops below $thresholdPercentage%",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                    androidx.compose.material3.Slider(
                        value = autoLockDistanceThreshold,
                        onValueChange = { viewModel.setAutoLockDistanceThreshold(it) },
                        valueRange = 0f..1f,
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = SuccessTeal,
                            activeTrackColor = SuccessTeal,
                            inactiveTrackColor = LightSurfaceAlt
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Near (5m)", color = TextSecondary, fontSize = 12.sp)
                        Text("Far (20m)", color = TextSecondary, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logout Button
            Button(
                onClick = { viewModel.logout() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPrimaryColor.current.copy(alpha = 0.1f),
                    contentColor = LocalPrimaryColor.current
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

// --- Bluetooth MAC Spoofing Card ---
@Composable
fun BluetoothMacSpoofCard(
    currentMac: String,
    isSpoofed: Boolean,
    statusMessage: String,
    savedDevices: List<com.commvault.commlink.domain.model.Workstation>,
    onApplyMac: (String) -> Unit,
    onRestoreMac: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var inputMode by remember { mutableStateOf<MacInputMode?>(null) } // null=collapsed, DEVICE=pick, MANUAL=type
    var manualMacInput by remember { mutableStateOf("") }

    val spoofColor = Color(0xFFFF6B35) // Orange for spoofing

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { isExpanded = !isExpanded },
        color = LightSurface,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.5.dp,
            if (isSpoofed) spoofColor.copy(alpha = 0.5f) else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isSpoofed) spoofColor.copy(alpha = 0.15f) else LightSurfaceAlt,
                            RoundedCornerShape(10.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (isSpoofed) spoofColor else TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Bluetooth MAC Address",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (currentMac.isNotBlank()) currentMac else "Tap to read",
                        color = if (isSpoofed) spoofColor else TextSecondary,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSpoofed) FontWeight.Bold else FontWeight.Normal
                    )
                    if (isSpoofed) {
                        Text(
                            text = "⚠ Spoofed (temporary)",
                            color = spoofColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextTertiary
                )
            }

            // Status message
            if (statusMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val statusColor = when {
                    statusMessage.contains("success", ignoreCase = true) || statusMessage.contains("restored", ignoreCase = true) -> SuccessTeal
                    statusMessage.contains("failed", ignoreCase = true) || statusMessage.contains("Invalid", ignoreCase = true) -> Color.Red
                    statusMessage.contains("Applying") -> LocalPrimaryColor.current
                    else -> TextSecondary
                }
                Text(
                    text = statusMessage,
                    color = statusColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Expanded content
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    // Restore button (when spoofed)
                    if (isSpoofed) {
                        Button(
                            onClick = onRestoreMac,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessTeal)
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restore Original MAC", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Two options: Copy from device or manual entry
                    Text(
                        text = "CHANGE MAC ADDRESS",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Option 1: Copy from saved device
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (inputMode == MacInputMode.DEVICE) LightSurface else LightSurfaceAlt,
                            shadowElevation = if (inputMode == MacInputMode.DEVICE) 3.dp else 0.dp,
                            border = if (inputMode == MacInputMode.DEVICE) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, LocalPrimaryColor.current)
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    inputMode = if (inputMode == MacInputMode.DEVICE) null else MacInputMode.DEVICE
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = if (inputMode == MacInputMode.DEVICE) LocalPrimaryColor.current else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "From Device",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (inputMode == MacInputMode.DEVICE) LocalPrimaryColor.current else TextPrimary
                                )
                            }
                        }

                        // Option 2: Manual entry
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (inputMode == MacInputMode.MANUAL) LightSurface else LightSurfaceAlt,
                            shadowElevation = if (inputMode == MacInputMode.MANUAL) 3.dp else 0.dp,
                            border = if (inputMode == MacInputMode.MANUAL) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, LocalPrimaryColor.current)
                            } else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    inputMode = if (inputMode == MacInputMode.MANUAL) null else MacInputMode.MANUAL
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = if (inputMode == MacInputMode.MANUAL) LocalPrimaryColor.current else TextTertiary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Type Manually",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (inputMode == MacInputMode.MANUAL) LocalPrimaryColor.current else TextPrimary
                                )
                            }
                        }
                    }

                    // --- DEVICE PICKER ---
                    AnimatedVisibility(visible = inputMode == MacInputMode.DEVICE) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            if (savedDevices.isEmpty()) {
                                Text(
                                    "No saved devices found. Pair a device first.",
                                    color = TextTertiary,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(8.dp)
                                )
                            } else {
                                savedDevices.forEach { device ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = LightSurfaceAlt,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clickable {
                                                onApplyMac(device.address)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    device.name,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    device.address,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = TextSecondary
                                                )
                                            }
                                            Text(
                                                "USE",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = LocalPrimaryColor.current
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- MANUAL ENTRY ---
                    AnimatedVisibility(visible = inputMode == MacInputMode.MANUAL) {
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            OutlinedTextField(
                                value = manualMacInput,
                                onValueChange = { newValue ->
                                    // Auto-format: insert colons as user types hex chars
                                    val cleaned = newValue.uppercase().filter { it in "0123456789ABCDEF:" }
                                    manualMacInput = formatMacInput(cleaned)
                                },
                                label = { Text("MAC Address") },
                                placeholder = { Text("XX:XX:XX:XX:XX:XX", fontFamily = FontFamily.Monospace) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = LocalPrimaryColor.current,
                                    unfocusedBorderColor = BorderColor
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp
                                ),
                                singleLine = true
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { onApplyMac(manualMacInput) },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LocalPrimaryColor.current),
                                enabled = manualMacInput.length == 17 // XX:XX:XX:XX:XX:XX
                            ) {
                                Text("Apply MAC Address", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class MacInputMode { DEVICE, MANUAL }

/** Auto-format raw hex input into XX:XX:XX:XX:XX:XX format */
private fun formatMacInput(raw: String): String {
    // Strip existing colons to get pure hex
    val hex = raw.replace(":", "").take(12) // Max 12 hex chars (6 octets)
    val sb = StringBuilder()
    hex.forEachIndexed { index, c ->
        sb.append(c)
        if (index % 2 == 1 && index < hex.length - 1) {
            sb.append(':')
        }
    }
    return sb.toString()
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
                    color = LocalPrimaryColor.current,
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
                    thumbColor = LocalPrimaryColor.current,
                    activeTrackColor = LocalPrimaryColor.current,
                    inactiveTrackColor = LightSurfaceAlt
                )
            )
        }
    }
}
