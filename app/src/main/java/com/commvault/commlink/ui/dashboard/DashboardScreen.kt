package com.commvault.commlink.ui.dashboard

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.R
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import kotlin.math.roundToInt

// ── ULTRA MODERN UI CONSTANTS ──
val PageBg = Color(0xFFF4F7FB) // Soft cool gray background
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceCardLight = Color(0xFFF8FAFC)
val TextTitle = Color(0xFF0F172A)
val TextSub = Color(0xFF64748B)
val BentoRadiusLg = 32.dp
val BentoRadiusMd = 24.dp
val BentoRadiusSm = 16.dp
val ShadowColor = Color(0xFF334155).copy(alpha = 0.08f)

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
    onNavigateToFileshare: () -> Unit,
    onNavigateToAudioBridge: () -> Unit,
    onNavigateToShortcuts: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val isConnecting = connectionState is HidDeviceManager.ConnectionState.Connecting
    val deviceName = (connectionState as? HidDeviceManager.ConnectionState.Connected)?.deviceName ?: ""
    val savedDevices by viewModel.savedDevices.collectAsState()
    
    val trackpadSensitivity by viewModel.trackpadSensitivity.collectAsState()
    
    val primary = LocalPrimaryColor.current

    Scaffold(
        containerColor = PageBg,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToSnippets,
                containerColor = primary
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Take Notes", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 90.dp, bottom = 100.dp, start = 20.dp, end = 20.dp)
            ) {
            
            // --- HERO SECTION: MODERN DEVICE CARD ---
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val haptic = LocalHapticFeedback.current

                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .shadow(32.dp, RoundedCornerShape(32.dp), spotColor = if (isConnected) primary.copy(alpha = 0.4f) else ShadowColor)
                            .clip(RoundedCornerShape(32.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF8FAFC))
                                )
                            )
                            .border(1.dp, Color.White, RoundedCornerShape(32.dp))
                            .pointerInput(isConnected) {
                                detectTapGestures(
                                    onTap = { if (!isConnected) onNavigateToPairing() }
                                )
                            }
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left side: Device Name & Status
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = if (isConnected) deviceName.ifEmpty { "Workstation" } else "No Device",
                                    color = TextTitle,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1).sp,
                                    lineHeight = 32.sp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(if (isConnected) primary.copy(alpha = 0.15f) else Color(0xFFF1F5F9), CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .shadow(4.dp, CircleShape, spotColor = primary)
                                            .background(if (isConnected) primary else Color.Gray, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isConnected) "CONNECTED" else "OFFLINE",
                                        color = if (isConnected) primary else TextSub,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            // Right side: Laptop Image
                            Box(
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.premium_device),
                                    contentDescription = "Laptop",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                    

                }
            }

            // --- DIRECT UNLOCK SLIDER (WHITE BACKGROUND TILE) ---
            item {
                BiDirectionalSwipeUnlock(
                    isConnected = isConnected,
                    primaryColor = primary,
                    onSwipeRight = {
                        if (viewModel.getSavedPassword().isEmpty()) {
                            android.widget.Toast.makeText(context, "Set password in Shield first", android.widget.Toast.LENGTH_SHORT).show()
                            onNavigateToPasswordManager()
                        } else {
                            viewModel.unlockWindows(wakeScreenFirst = false)
                            android.widget.Toast.makeText(context, "Direct Unlock triggered", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    onSwipeLeft = {
                        if (viewModel.getSavedPassword().isEmpty()) {
                            android.widget.Toast.makeText(context, "Set password in Shield first", android.widget.Toast.LENGTH_SHORT).show()
                            onNavigateToPasswordManager()
                        } else {
                            viewModel.unlockWindows(wakeScreenFirst = true)
                            android.widget.Toast.makeText(context, "Wake & Unlock triggered", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // --- BENTO GRID: VERTICAL AUDIO LEFT, ESSENTIALS RIGHT ---
            item {
                Text("Dashboard", color = TextTitle, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 12.dp))
                
                Row(modifier = Modifier.fillMaxWidth().height(220.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    
                    // VERTICAL AUDIO CONTROLS ON LEFT
                    VerticalMediaControlBar(
                        modifier = Modifier.width(64.dp).fillMaxHeight(),
                        viewModel = viewModel,
                        primaryColor = primary
                    )
                    
                    // ESSENTIALS ON RIGHT
                    Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        BentoCard(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            title = "Keyboard & Trackpad",
                            icon = Icons.Default.Keyboard,
                            primaryColor = primary,
                            accentColor = Color(0xFF3B82F6), // Blue
                            enabled = isConnected,
                            onClick = onNavigateToKeyboard
                        )
                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            BentoCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                title = "Presenter",
                                icon = Icons.Default.Slideshow,
                                primaryColor = primary,
                                accentColor = Color(0xFFF59E0B), // Amber
                                enabled = isConnected,
                                centerContent = true,
                                onClick = onNavigateToPresenter
                            )
                            BentoCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                title = "Files",
                                icon = Icons.Default.Folder,
                                primaryColor = primary,
                                accentColor = Color(0xFF8B5CF6), // Purple
                                enabled = true,
                                centerContent = true,
                                onClick = onNavigateToFileshare
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
            
            // --- QUICK ACTION PILLS ---
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ActionPill(title = "Lock PC", icon = Icons.Default.Lock, onClick = { viewModel.lockWindows() })
                    ActionPill(title = "Sync Text", icon = Icons.Default.Sync, onClick = {
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
                    })
                    ActionPill(title = "Shield", icon = Icons.Default.Security, onClick = onNavigateToPasswordManager)
                    ActionPill(title = "Settings", icon = Icons.Default.Settings, onClick = onNavigateToSettings)
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // --- ADVANCED TOOLS (IOS STYLE GROUPED LIST) ---
            item {
                Text("Advanced Tools", color = TextTitle, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 12.dp))
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(BentoRadiusMd), spotColor = ShadowColor)
                        .clip(RoundedCornerShape(BentoRadiusMd))
                        .background(SurfaceCard)
                ) {
                    val tools = listOf(
                        Triple("Shortcuts", Icons.Default.AutoAwesome, onNavigateToShortcuts),
                        Triple("Snippets", Icons.Default.Description, onNavigateToSnippets),
                        Triple("Automation", Icons.Default.SettingsSuggest, onNavigateToAutomation),
                        Triple("Tasks", Icons.Default.Checklist, onNavigateToTodo),
                        Triple("Chat", Icons.AutoMirrored.Filled.Chat, onNavigateToChat)
                    )
                    
                    tools.forEachIndexed { index, (title, icon, onClick) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick() }
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier.size(32.dp).background(primary.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon as ImageVector, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(title as String, color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSub, modifier = Modifier.size(16.dp))
                        }
                        if (index < tools.size - 1) {
                            HorizontalDivider(modifier = Modifier.padding(start = 68.dp), color = SurfaceCardLight, thickness = 1.dp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // --- SAVED DEVICES (IOS STYLE) ---
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Saved Devices", color = TextTitle, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("Add New", color = primary, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onNavigateToPairing() })
                }
                Spacer(modifier = Modifier.height(12.dp))
                
                if (savedDevices.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp).background(SurfaceCard, RoundedCornerShape(BentoRadiusMd)), contentAlignment = Alignment.Center) {
                        Text("No devices saved", color = TextSub, fontSize = 14.sp)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(BentoRadiusMd), spotColor = ShadowColor)
                            .clip(RoundedCornerShape(BentoRadiusMd))
                            .background(SurfaceCard)
                    ) {
                        savedDevices.forEachIndexed { index, device ->
                            val isConn = (deviceName == device.name || deviceName == device.address)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.connectDevice(device.address) }
                                    .padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Laptop, contentDescription = null, tint = if(isConn) primary else TextSub, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(device.name, color = TextTitle, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Text(if (isConn) "Connected" else device.address, color = if (isConn) primary else TextSub, fontSize = 12.sp)
                                    }
                                }
                                if (isConn) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = primary, modifier = Modifier.size(24.dp))
                                } else {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSub, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < savedDevices.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(start = 64.dp), color = SurfaceCardLight, thickness = 1.dp)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            } // end item
        } // end LazyColumn
            
        // Fading App Bar Layer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            PageBg,
                            PageBg.copy(alpha = 0.95f),
                            PageBg.copy(alpha = 0.7f),
                            PageBg.copy(alpha = 0.0f)
                        )
                    )
                )
        ) {
            ModernTopBar(
                onOpenDrawer = onOpenDrawer,
                isConnected = isConnected,
                primaryColor = primary,
                onConnectClick = { if (isConnected) viewModel.disconnect() else onNavigateToPairing() }
            )
        }
    } // end inner Box
} // end Scaffold block
} // end DashboardScreen

// ── CUSTOM MODERN COMPONENTS ──

@Composable
fun BiDirectionalSwipeUnlock(
    isConnected: Boolean,
    primaryColor: Color,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX, 
        animationSpec = if (offsetX == 0f) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow) else tween(0)
    )
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .shadow(16.dp, RoundedCornerShape(36.dp), spotColor = ShadowColor)
            .clip(RoundedCornerShape(36.dp))
            .background(SurfaceCard) // White background tile
            .padding(8.dp)
    ) {
        val maxDrag = with(LocalDensity.current) { 
            (maxWidth.toPx() / 2f) - (56.dp.toPx() / 2f) - 8.dp.toPx() 
        }
        
        // Calculate dynamic alpha for text fading
        val fadeThreshold = maxDrag * 0.4f
        val leftTextAlpha = if (offsetX > 0f) 0f else (1f - (Math.abs(offsetX) / fadeThreshold)).coerceIn(0f, 1f)
        val rightTextAlpha = if (offsetX < 0f) 0f else (1f - (Math.abs(offsetX) / fadeThreshold)).coerceIn(0f, 1f)

        // Background text
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("← Wake", color = TextSub.copy(alpha = leftTextAlpha), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("Direct →", color = TextSub.copy(alpha = rightTextAlpha), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        // Draggable Thumb
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    .size(56.dp)
                    .shadow(12.dp, CircleShape, spotColor = if (isConnected) primaryColor else Color.Gray)
                    .background(if (isConnected) primaryColor else Color.Gray, CircleShape)
                    .pointerInput(isConnected) {
                        if (!isConnected) return@pointerInput
                        
                        detectHorizontalDragGestures(
                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            onDragEnd = {
                                if (offsetX > maxDrag * 0.7f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSwipeRight()
                                } else if (offsetX < -maxDrag * 0.7f) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSwipeLeft()
                                }
                                offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = (offsetX + dragAmount).coerceIn(-maxDrag, maxDrag)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@Composable
fun VerticalMediaControlBar(modifier: Modifier, viewModel: CommLinkViewModel, primaryColor: Color) {
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Consume all leftover scroll so it never bubbles up to the parent LazyColumn
                return available
            }
        }
    }

    Column(
        modifier = modifier
            .height(200.dp) // Fixed height to enforce internal scrolling
            .shadow(12.dp, RoundedCornerShape(BentoRadiusLg), spotColor = Color(0xFF0F172A).copy(alpha = 0.2f))
            .background(Color(0xFF0F172A), RoundedCornerShape(BentoRadiusLg)) // Extremely dark pill for contrast
            .padding(vertical = 12.dp)
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        IconButton(onClick = { viewModel.sendMediaVolumeUp() }) { 
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Vol+", tint = Color.White.copy(alpha = 0.8f)) 
        }
        IconButton(onClick = { viewModel.sendMediaPrev() }) { 
            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White.copy(alpha = 0.8f)) 
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(primaryColor, CircleShape)
                .clickable { viewModel.sendMediaPlayPause() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = { viewModel.sendMediaNext() }) { 
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White.copy(alpha = 0.8f)) 
        }
        IconButton(onClick = { viewModel.sendMediaVolumeDown() }) { 
            Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Vol-", tint = Color.White.copy(alpha = 0.8f)) 
        }
        IconButton(onClick = { viewModel.sendMediaMute() }) { 
            Icon(Icons.Default.VolumeOff, contentDescription = "Mute", tint = Color.White.copy(alpha = 0.8f)) 
        }
    }
}

@Composable
fun ModernTopBar(onOpenDrawer: () -> Unit, isConnected: Boolean, primaryColor: Color, onConnectClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).background(SurfaceCard, CircleShape)
                .shadow(4.dp, CircleShape, spotColor = ShadowColor)
                .clickable { onOpenDrawer() }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.commvault_logo),
                contentDescription = "Menu",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit
            )
        }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COMM", color = TextTitle, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
            Text("LINK", color = primaryColor, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        
        Box(
            modifier = Modifier.size(44.dp).background(if (isConnected) primaryColor.copy(alpha = 0.15f) else SurfaceCard, CircleShape)
                .shadow(if (isConnected) 0.dp else 4.dp, CircleShape, spotColor = ShadowColor)
                .clickable { onConnectClick() }
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, 
                contentDescription = "Bluetooth", 
                tint = if (isConnected) primaryColor else TextSub,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ModernSegmentedToggle(isUsb: Boolean, primaryColor: Color, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .width(190.dp)
            .height(40.dp)
            .background(SurfaceCard, RoundedCornerShape(20.dp))
            .shadow(4.dp, RoundedCornerShape(20.dp), spotColor = ShadowColor)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(if (!isUsb) PageBg else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable { onToggle(false) },
            contentAlignment = Alignment.Center
        ) {
            Text("Bluetooth", color = if (!isUsb) TextTitle else TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight()
                .background(if (isUsb) PageBg else Color.Transparent, RoundedCornerShape(16.dp))
                .clickable { onToggle(true) },
            contentAlignment = Alignment.Center
        ) {
            Text("USB", color = if (isUsb) TextTitle else TextSub, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BentoCard(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    primaryColor: Color,
    accentColor: Color,
    enabled: Boolean,
    large: Boolean = false,
    centerContent: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (enabled) SurfaceCard else SurfaceCardLight
    val contentColor = if (enabled) TextTitle else TextSub
    
    Box(
        modifier = modifier
            .shadow(if (enabled) 12.dp else 2.dp, RoundedCornerShape(BentoRadiusLg), spotColor = ShadowColor)
            .clip(RoundedCornerShape(BentoRadiusLg))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(if (large) 20.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(), 
            verticalArrangement = if (centerContent) Arrangement.Center else Arrangement.SpaceBetween,
            horizontalAlignment = if (centerContent) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(if (large) 56.dp else 40.dp)
                    .background(
                        if (enabled) Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.7f))) 
                        else Brush.linearGradient(listOf(Color.LightGray, Color.Gray)), 
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (large) 28.dp else 20.dp))
            }
            if (centerContent) Spacer(modifier = Modifier.height(12.dp))
            Text(
                title, 
                color = contentColor, 
                fontSize = if (large) 20.sp else 14.sp, 
                fontWeight = FontWeight.Black,
                textAlign = if (centerContent) TextAlign.Center else TextAlign.Start,
                lineHeight = if (large) 24.sp else 18.sp
            )
        }
    }
}

@Composable
fun ActionPill(title: String, icon: ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .shadow(8.dp, CircleShape, spotColor = ShadowColor)
                .background(SurfaceCard, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = title, tint = TextTitle, modifier = Modifier.size(28.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, color = TextTitle, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
