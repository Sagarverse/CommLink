package com.commvault.commlink.ui.dashboard

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.R
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.components.CeramicCard
import com.commvault.commlink.ui.components.EmeraldButton
import com.commvault.commlink.ui.theme.*

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
    onNavigateToSettings: () -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToTodo: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val isConnecting = connectionState is HidDeviceManager.ConnectionState.Connecting
    val deviceName = (connectionState as? HidDeviceManager.ConnectionState.Connected)?.deviceName ?: ""
    val savedDevices by viewModel.savedDevices.collectAsState()
    val currentSpeed by viewModel.typingSpeed.collectAsState()
    
    val isTextPushing by viewModel.isTextPushing.collectAsState()
    val isPushPaused by viewModel.isPushPaused.collectAsState()
    
    var connectingAddress by remember { mutableStateOf<String?>(null) }
    var previousState by remember { mutableStateOf<HidDeviceManager.ConnectionState?>(null) }

    LaunchedEffect(connectionState) {
        if (previousState != null && previousState != connectionState) {
            when (connectionState) {
                is HidDeviceManager.ConnectionState.Connected -> {
                    val name = (connectionState as HidDeviceManager.ConnectionState.Connected).deviceName
                    android.widget.Toast.makeText(context, "Connected to $name", android.widget.Toast.LENGTH_SHORT).show()
                    connectingAddress = null
                }
                is HidDeviceManager.ConnectionState.Disconnected -> {
                    if (previousState is HidDeviceManager.ConnectionState.Connecting) {
                        android.widget.Toast.makeText(context, "Failed to connect", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    connectingAddress = null
                }
                else -> {}
            }
        }
        previousState = connectionState
    }
    
    // Notes State
    val prefs = context.getSharedPreferences("commlink_prefs", android.content.Context.MODE_PRIVATE)
    var showNotesDialog by remember { mutableStateOf(false) }
    var notesText by remember { mutableStateOf(prefs.getString("quick_notes", "") ?: "") }
    
    // Dynamic theme colors
    val primary = LocalPrimaryColor.current
    val primaryLight = LocalPrimaryLight.current
    val primaryDark = LocalPrimaryDark.current

    if (showNotesDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { 
                prefs.edit().putString("quick_notes", notesText).apply()
                showNotesDialog = false 
            },
            title = { Text("Quick Notes", color = SecondaryDark, fontWeight = FontWeight.Bold) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = notesText,
                    onValueChange = { notesText = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        cursorColor = primary
                    ),
                    placeholder = { Text("Write something...") }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        prefs.edit().putString("quick_notes", notesText).apply()
                        showNotesDialog = false
                    }
                ) {
                    Text("Save", color = primary, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color.White
        )
    }

    Scaffold(
        containerColor = PageBackground,
        topBar = {
            Surface(color = PageBackground, shadowElevation = 0.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Profile/Logo
                    Box(
                        modifier = Modifier.size(40.dp).background(SecondaryDark, CircleShape).clickable { onOpenDrawer() }.clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.commvault_logo),
                            contentDescription = "Menu",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    // Title
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "COMM LINK",
                            color = SecondaryDark,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(modifier = Modifier.size(6.dp).background(primary, CircleShape))
                    }
                    
                    // Connection & Push Controls
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTextPushing) {
                            IconButton(onClick = { viewModel.toggleTextPushPause() }) {
                                Icon(
                                    if (isPushPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = "Toggle Push",
                                    tint = primary
                                )
                            }
                            IconButton(onClick = { viewModel.stopTextPush() }) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "Stop Push",
                                    tint = Color.Red
                                )
                            }
                        } else {
                            IconButton(onClick = { 
                                if (isConnected) viewModel.disconnect() else onNavigateToPairing() 
                            }) {
                                Icon(
                                    if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, 
                                    contentDescription = "Bluetooth", 
                                    tint = if (isConnected) primary else NeutralSlate
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            androidx.compose.material3.FloatingActionButton(
                onClick = { showNotesDialog = true },
                containerColor = primary,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.padding(bottom = 16.dp, end = 8.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Quick Notes", modifier = Modifier.size(24.dp))
            }
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. Minimalist Hero Section
            item(span = { GridItemSpan(2) }) {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToPairing() },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Premium floating image
                    Image(
                        painter = painterResource(id = R.drawable.premium_device),
                        contentDescription = "Connected Devices",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(32.dp)),
                        contentScale = ContentScale.Fit
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Connection Status Pill
                    Box(
                        modifier = Modifier
                            .background(
                                if (isConnected) primary.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.1f), 
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(
                                if (isConnected) primary else Color.Gray, 
                                CircleShape
                            ))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                if (isConnected) "Connected" else if (isConnecting) "Linking..." else "Disconnected", 
                                color = if (isConnected) primary else NeutralSlate, 
                                fontSize = 13.sp, 
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Device Name (Huge and clean)
                    Text(
                        if (isConnected) deviceName.ifEmpty { "Galaxy S23 Ultra" } else "No Device", 
                        color = SecondaryDark, 
                        fontSize = 24.sp, 
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
                    )
                    
                    // Real device info & Actions
                    if (isConnected) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Connected via HID Protocol", 
                            color = NeutralSlate, 
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Speed Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("INPUT SPEED", color = NeutralSlate, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            
                            val speeds = listOf(
                                "0.5x" to 240L,
                                "1x" to 120L,
                                "1.5x" to 80L,
                                "2x" to 40L,
                                "MAX" to 0L
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                speeds.forEach { (label, speed) ->
                                    val active = currentSpeed == speed
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (active) primary else Color.Gray.copy(alpha = 0.1f))
                                            .clickable { viewModel.setTypingSpeed(speed) }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (active) Color.White else SecondaryDark
                                        )
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Direct Unlock Button
                        EmeraldButton(
                            text = "Unlock PC",
                            modifier = Modifier.fillMaxWidth(0.5f),
                            onClick = { viewModel.unlockWindows(wakeScreenFirst = true) }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Media Controls Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White)
                                .padding(vertical = 12.dp, horizontal = 24.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = { viewModel.sendMediaVolumeDown() }) {
                                    Icon(Icons.Default.VolumeDown, contentDescription = "Vol-", tint = SecondaryDark)
                                }
                                IconButton(onClick = { viewModel.sendMediaPrev() }) {
                                    Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = SecondaryDark)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(primary.copy(alpha = 0.1f), CircleShape)
                                        .clickable { viewModel.sendMediaPlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play/Pause", tint = primary, modifier = Modifier.size(24.dp))
                                }
                                IconButton(onClick = { viewModel.sendMediaNext() }) {
                                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = SecondaryDark)
                                }
                                IconButton(onClick = { viewModel.sendMediaVolumeUp() }) {
                                    Icon(Icons.Default.VolumeUp, contentDescription = "Vol+", tint = SecondaryDark)
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Tap to pair a new device", 
                            color = NeutralSlate, 
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 2. Grid Items - Reordered for UX priority

            item {
                PremiumGridCard(title = "Lock", icon = Icons.Default.Lock, enabled = isConnected, onClick = { viewModel.lockWindows() }, primary = primary)
            }
            item {
                PremiumGridCard(
                    title = "Direct Unlock", 
                    icon = Icons.Default.LockOpen, 
                    enabled = isConnected, 
                    onClick = { 
                        if (viewModel.getSavedPassword().isEmpty()) {
                            android.widget.Toast.makeText(context, "Please set password in Shield first", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.unlockWindows(wakeScreenFirst = false)
                        }
                    }, 
                    primary = primary
                )
            }
            item {
                PremiumGridCard(title = "Keyboard", icon = Icons.Default.Keyboard, enabled = isConnected, onClick = onNavigateToKeyboard, primary = primary)
            }
            item {
                PremiumGridCard(
                    title = "Sync Clipboard", 
                    icon = Icons.Default.ContentPaste, 
                    enabled = isConnected,
                    primary = primary,
                    onClick = {
                        try {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = clipboardManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotEmpty()) {
                                    viewModel.sendText(text)
                                    android.widget.Toast.makeText(context, "Sent to PC!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) { }
                    }
                )
            }
            item {
                PremiumGridCard(title = "Automation", icon = Icons.Default.Settings, enabled = true, onClick = onNavigateToAutomation, primary = primary)
            }
            item {
                PremiumGridCard(title = "Chat", icon = Icons.Default.Chat, enabled = true, onClick = onNavigateToChat, primary = primary)
            }
            item {
                PremiumGridCard(title = "Snippets", icon = Icons.Default.Description, enabled = true, onClick = onNavigateToSnippets, primary = primary)
            }

            item {
                PremiumGridCard(title = "Shield", icon = Icons.Default.Security, enabled = true, onClick = onNavigateToPasswordManager, primary = primary)
            }
            item {
                PremiumGridCard(title = "To-Do", icon = Icons.Default.Assignment, enabled = true, onClick = onNavigateToTodo, primary = primary)
            }
            item {
                PremiumGridCard(title = "Microsoft Apps", icon = Icons.Default.Apps, enabled = isConnected, primary = primary, onClick = { viewModel.runMacroScript("GUI r\nDELAY 200\nSTRING https://myapplications.microsoft.com\nENTER") })
            }
            item {
                PremiumGridCard(title = "Workspace", icon = Icons.Default.Work, enabled = true, primary = primary, onClick = { /* Show workspace dialog */ })
            }
            item {
                PremiumGridCard(title = "Updates", icon = Icons.Default.SystemUpdate, enabled = true, primary = primary, onClick = onCheckUpdates)
            }
            
            // Saved Devices Section
            item(span = { GridItemSpan(2) }) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Saved Devices",
                    color = SecondaryDark,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Real Saved Devices
            if (savedDevices.isEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    Text(
                        text = "No saved devices yet",
                        color = NeutralSlate,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            } else {
                items(savedDevices.size, span = { GridItemSpan(2) }) { index ->
                    val device = savedDevices[index]
                    
                    val isThisDeviceConnected = isConnected && (deviceName == device.name || deviceName == device.address)
                    val isThisDeviceConnecting = isConnecting && (connectingAddress == device.address || connectingAddress == null)
                    
                    Spacer(modifier = Modifier.height(if (index == 0) 0.dp else 16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .clickable {
                                if (!isThisDeviceConnected && !isThisDeviceConnecting) {
                                    connectingAddress = device.address
                                    android.widget.Toast.makeText(context, "Connecting to ${device.name.ifEmpty { device.address }}...", android.widget.Toast.LENGTH_SHORT).show()
                                    viewModel.connectDevice(device.address)
                                }
                            }
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(primary.copy(alpha = 0.1f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Devices, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(device.name, color = SecondaryDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    Text(device.address, color = NeutralSlate, fontSize = 12.sp)
                                }
                            }
                            
                            if (isThisDeviceConnected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Connected", color = Color(0xFF10B981), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (isThisDeviceConnecting) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = primary)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connecting...", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Connect", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(2) }) { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

@Composable
fun PremiumGridCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    enabled: Boolean = true,
    primary: Color = LocalPrimaryColor.current,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(120.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White)
            .border(1.dp, Color.Gray.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (enabled) primary.copy(alpha = 0.1f) else CardSurface, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (enabled) primary else NeutralSlate, modifier = Modifier.size(22.dp))
            }
            Text(
                text = title,
                color = if (enabled) SecondaryDark else NeutralSlate,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
