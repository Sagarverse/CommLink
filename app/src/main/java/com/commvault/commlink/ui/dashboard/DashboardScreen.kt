package com.commvault.commlink.ui.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.commvault.commlink.ui.components.bounceClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.R
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.components.MacroControlBar
import com.commvault.commlink.ui.components.bounceClick
import com.commvault.commlink.ui.components.staggeredFadeIn
import com.commvault.commlink.ui.theme.*

import android.content.Context
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: CommLinkViewModel,
    onOpenDrawer: () -> Unit = {},
    onNavigateToPairing: () -> Unit,
    onNavigateToKeyboard: () -> Unit,
    onNavigateToSnippets: () -> Unit,
    onNavigateToAutomation: () -> Unit,
    onNavigateToPresenter: () -> Unit,
    onNavigateToPasswordManager: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val context = LocalContext.current

    val email by viewModel.email.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val savedDevices by viewModel.savedDevices.collectAsState()
    var showWorkspaceDialog by remember { mutableStateOf(false) }

    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val isConnecting = connectionState is HidDeviceManager.ConnectionState.Connecting
    val connectedDeviceName = (connectionState as? HidDeviceManager.ConnectionState.Connected)?.deviceName ?: ""

    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    
    val statusAlpha = if (isConnecting) pulseAlpha else 1f

    if (showWorkspaceDialog) {
        AlertDialog(
            onDismissRequest = { showWorkspaceDialog = false },
            confirmButton = {
                TextButton(onClick = { showWorkspaceDialog = false }) {
                    Text("OK", color = CommvaultPink)
                }
            },
            title = { Text("Workspace Feature", color = TextPrimary) },
            text = { Text("The workspace environment synchronization feature will be added in a future update.", color = TextSecondary) },
            containerColor = LightSurface,
            textContentColor = TextSecondary
        )
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 3.dp) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = R.drawable.commvault_logo),
                                contentDescription = "Commvault Logo",
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CommLink",
                                    color = CommvaultNavy,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = email,
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                    }
                },
                actions = {
                    MacroControlBar(viewModel = viewModel)
                    if (isConnected) {
                        IconButton(
                            onClick = { viewModel.disconnect() },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .background(CommvaultPink.copy(alpha = 0.1f), CircleShape)
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = "Disconnect", tint = CommvaultPink)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightSurface
                )
            )
            } // Close Surface
        } // Close topBar
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Connection Status Panel
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .alpha(statusAlpha)
                    .clickable { 
                        if (isConnected) {
                            onNavigateToKeyboard()
                        } else {
                            onNavigateToPairing()
                        }
                    },
                 color = LightSurface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 6.dp,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, 
                    if (isConnected) SuccessTeal.copy(alpha = 0.5f) else BorderColor
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    when {
                                        isConnected -> SuccessTeal.copy(alpha = 0.1f)
                                        isConnecting -> CommvaultPink.copy(alpha = 0.1f)
                                        else -> CommvaultPink.copy(alpha = 0.1f)
                                    },
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = CommvaultPink,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.LaptopWindows else Icons.Default.BluetoothSearching,
                                    contentDescription = null,
                                    tint = if (isConnected) SuccessTeal else CommvaultPink,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        Column {
                            Text(
                                text = when {
                                    isConnected -> "Connected"
                                    isConnecting -> "Connecting..."
                                    else -> "Disconnected"
                                },
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    isConnected -> connectedDeviceName
                                    isConnecting -> "Establishing connection..."
                                    else -> "Tap to scan & connect"
                                },
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when {
                                    isConnected -> SuccessTeal
                                    isConnecting -> CommvaultPink
                                    else -> Color.Gray
                                }, 
                                CircleShape
                            )
                    )
                }
            }

            // Central Actions Grid (Unlock, Lock, Workspace)
            Text(
                text = "SYSTEM CONTROLS",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(130.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Unlock",
                    icon = Icons.Default.LockOpen,
                    accentColor = SuccessTeal,
                    enabled = isConnected,
                    onClick = { viewModel.unlockWindows() }
                )
                
                DashboardActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Lock",
                    icon = Icons.Default.Lock,
                    accentColor = CommvaultPink,
                    enabled = isConnected,
                    onClick = { viewModel.lockWindows() }
                )
                
                DashboardActionCard(
                    modifier = Modifier.weight(1f),
                    title = "Workspace",
                    icon = Icons.Default.Work,
                    accentColor = Color(0xFF00B0FF),
                    enabled = true,
                    onClick = { showWorkspaceDialog = true }
                )
            }

            // Helper Utilities
            Text(
                text = "WORKSPACE UTILITIES",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Keyboard & Mouse",
                        icon = Icons.Default.Keyboard,
                        enabled = isConnected,
                        onClick = onNavigateToKeyboard
                    )
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Text Templates",
                        icon = Icons.Default.Description,
                        enabled = true,
                        onClick = onNavigateToSnippets
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Automation Hub",
                        icon = Icons.Default.Settings,
                        enabled = true,
                        onClick = onNavigateToAutomation
                    )
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Presenter Remote",
                        icon = Icons.Default.Slideshow,
                        enabled = true,
                        onClick = onNavigateToPresenter
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Password Manager",
                        icon = Icons.Default.Fingerprint,
                        enabled = true,
                        onClick = onNavigateToPasswordManager
                    )
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "Sync Clipboard",
                        icon = Icons.Default.ContentPaste,
                        enabled = isConnected,
                        onClick = {
                            try {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboardManager.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotEmpty()) {
                                        viewModel.sendText(text)
                                        android.widget.Toast.makeText(context, "Phone clipboard typed onto PC!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Clipboard empty!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Clipboard empty!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Clipboard access error!", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    UtilityGridCard(
                        modifier = Modifier.weight(1f),
                        title = "My Apps (Microsoft)",
                        icon = Icons.Default.Apps,
                        enabled = isConnected,
                        onClick = { viewModel.runMacroScript("GUI r\nDELAY 200\nSTRING https://myapplications.microsoft.com\nENTER") }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }

            // Recent Workstations Header
            Text(
                text = "SAVED WORKSTATIONS",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
            )

            if (savedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp)
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(LightSurfaceAlt)
                        .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No saved machines found.\nTap status card to search.",
                        color = TextTertiary,
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(savedDevices) { workstation ->
                        val isThisConnected = isConnected && connectedDeviceName == workstation.name
                        // We assume it's connecting to this workstation if it's currently connecting and it was just tapped
                        // But since we don't have the active connecting device's name easily without checking the MAC,
                        // we can just use the global isConnecting state and disable clicks.
                        Surface(
                            modifier = Modifier
                                .width(150.dp)
                                .height(120.dp)
                                .bounceClick(enabled = !isConnecting) {
                                    viewModel.connectDevice(workstation.address)
                                },
                            color = if (isThisConnected) LightSurfaceAlt else LightSurface,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isThisConnected) SuccessTeal else BorderColor
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(LightSurfaceAlt, RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LaptopWindows,
                                        contentDescription = null,
                                        tint = if (isThisConnected) SuccessTeal else TextTertiary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                
                                Column {
                                    Text(
                                        text = workstation.name,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isThisConnected) "Connected" else workstation.address,
                                        color = if (isThisConnected) SuccessTeal else TextTertiary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardActionCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    accentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .bounceClick(enabled = enabled) { onClick() },
        color = if (enabled) LightSurface else LightSurfaceAlt,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = if (enabled) 4.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (enabled) BorderColor else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (enabled) accentColor.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) accentColor else Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun UtilityGridCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(72.dp)
            .bounceClick(enabled = enabled) { onClick() },
        color = if (enabled) LightSurface else LightSurfaceAlt,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (enabled) 3.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) BorderColor else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (enabled) CommvaultPink.copy(alpha = 0.1f) else Color.Gray.copy(alpha = 0.05f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) CommvaultPink else Color.Gray.copy(alpha = 0.3f),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = if (enabled) TextPrimary else TextTertiary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2
            )
        }
    }
}
