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
import androidx.compose.ui.graphics.graphicsLayer
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

// ── BEAST LEVEL UI: PREMIUM DARK GLASS THEME ──
val DarkBg = Color(0xFF09090B)
val GlassSurface = Color(0xFF18181B).copy(alpha = 0.65f)
val GlassBorder = Color(0xFF27272A).copy(alpha = 0.8f)
val AccentCyan = Color(0xFF06B6D4)
val AccentPurple = Color(0xFF8B5CF6)
val TextTitleDark = Color(0xFFF8FAFC)
val TextSubDark = Color(0xFF94A3B8)
val BentoRadiusLg = 36.dp
val BentoRadiusMd = 24.dp
val BentoRadiusSm = 16.dp

@Composable
fun AnimatedMeshBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val rotation1 by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)), label = "rot1"
    )
    val rotation2 by infiniteTransition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "rot2"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Reverse), label = "pulse"
    )

    Box(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Box(
            modifier = Modifier
                .offset(x = (-150).dp, y = (-100).dp)
                .size(500.dp)
                .graphicsLayer(rotationZ = rotation1, alpha = pulseAlpha)
                .background(Brush.radialGradient(listOf(AccentPurple.copy(alpha = 0.3f), Color.Transparent)), CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = 100.dp, y = 200.dp)
                .size(600.dp)
                .graphicsLayer(rotationZ = rotation2, alpha = pulseAlpha)
                .background(Brush.radialGradient(listOf(AccentCyan.copy(alpha = 0.25f), Color.Transparent)), CircleShape)
        )
    }
}

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
    onNavigateToShortcuts: () -> Unit,
    onNavigateToAiAssistant: () -> Unit = {},
    onNavigateToAgent: () -> Unit = {},
    onCheckUpdates: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val deviceName = (connectionState as? HidDeviceManager.ConnectionState.Connected)?.deviceName ?: ""
    val savedDevices by viewModel.savedDevices.collectAsState()
    
    val primary = AccentCyan // Switch to Cyan for dark theme accent

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedMeshBackground()

        Scaffold(
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = onNavigateToAiAssistant,
                    containerColor = AccentPurple,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.shadow(16.dp, RoundedCornerShape(20.dp), spotColor = AccentPurple)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "AI Assistant")
                }
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 100.dp, bottom = 120.dp, start = 24.dp, end = 24.dp)
            ) {
                
                // --- HERO: 3D GLASS DEVICE CARD ---
                item {
                    val haptic = LocalHapticFeedback.current
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(32.dp, RoundedCornerShape(BentoRadiusLg), spotColor = if (isConnected) AccentCyan.copy(alpha = 0.5f) else Color.Black)
                            .clip(RoundedCornerShape(BentoRadiusLg))
                            .background(Brush.linearGradient(listOf(GlassSurface, Color(0xFF0F0F13).copy(alpha=0.8f))))
                            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha=0.3f), Color.Transparent)), RoundedCornerShape(BentoRadiusLg))
                            .pointerInput(isConnected) {
                                detectTapGestures(onTap = { if (!isConnected) onNavigateToPairing() })
                            }
                            .padding(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Info Column
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isConnected) "SYSTEM\nONLINE" else "SYSTEM\nOFFLINE",
                                    color = if (isConnected) AccentCyan else TextSubDark,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 4.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (isConnected) deviceName.ifEmpty { "Workstation" } else "No Device",
                                    color = TextTitleDark,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-1).sp,
                                    lineHeight = 34.sp
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(if (isConnected) AccentCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f), CircleShape)
                                        .border(1.dp, if (isConnected) AccentCyan.copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .shadow(8.dp, CircleShape, spotColor = if (isConnected) AccentCyan else Color.Transparent)
                                            .background(if (isConnected) AccentCyan else Color.Gray, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isConnected) "CONNECTED" else "TAP TO PAIR",
                                        color = if (isConnected) AccentCyan else TextSubDark,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))

                            // Laptop Mockup
                            Box(
                                modifier = Modifier.width(140.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    // Screen
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .aspectRatio(16f / 10f)
                                            .background(Color(0xFF000000), RoundedCornerShape(8.dp))
                                            .border(2.dp, Color(0xFF27272A), RoundedCornerShape(8.dp))
                                            .padding(4.dp)
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.drawable.laptop_device),
                                            contentDescription = "Screen",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(3.dp))
                                        )
                                        Box(
                                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-2).dp).size(2.dp).background(Color(0xFF111111), CircleShape)
                                        )
                                        if (!isConnected) {
                                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
                                        }
                                    }
                                    // Deck
                                    Box(
                                        modifier = Modifier
                                            .width(145.dp)
                                            .height(8.dp)
                                            .background(Brush.verticalGradient(listOf(Color(0xFF71717A), Color(0xFF3F3F46))), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                    ) {
                                        Box(
                                            modifier = Modifier.align(Alignment.TopCenter).width(24.dp).height(2.dp).background(Color(0xFF27272A), RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // --- DIRECT UNLOCK SLIDER (NEON THEME) ---
                item {
                    BiDirectionalSwipeUnlock(
                        isConnected = isConnected,
                        primaryColor = AccentCyan,
                        onSwipeRight = {
                            if (viewModel.getSavedPassword().isEmpty()) {
                                android.widget.Toast.makeText(context, "Set password in Shield first", android.widget.Toast.LENGTH_SHORT).show()
                                onNavigateToPasswordManager()
                            } else {
                                viewModel.unlockWindows(wakeScreenFirst = false)
                            }
                        },
                        onSwipeLeft = {
                            if (viewModel.getSavedPassword().isEmpty()) {
                                android.widget.Toast.makeText(context, "Set password in Shield first", android.widget.Toast.LENGTH_SHORT).show()
                                onNavigateToPasswordManager()
                            } else {
                                viewModel.unlockWindows(wakeScreenFirst = true)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }

                // --- PREMIUM BENTO GRID ---
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Core Modules", color = TextTitleDark, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Icon(Icons.Default.Dashboard, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth().height(230.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        
                        // MEDIA STRIP
                        VerticalMediaControlBar(
                            modifier = Modifier.width(70.dp).fillMaxHeight(),
                            viewModel = viewModel,
                            primaryColor = AccentPurple
                        )
                        
                        // BENTO RIGHT
                        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            BentoCard(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                title = "Keyboard & Trackpad",
                                icon = Icons.Default.Keyboard,
                                primaryColor = AccentCyan,
                                accentColor = AccentCyan,
                                enabled = isConnected,
                                large = true,
                                onClick = onNavigateToKeyboard
                            )
                            Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                BentoCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Present",
                                    icon = Icons.Default.Slideshow,
                                    primaryColor = AccentCyan,
                                    accentColor = Color(0xFFF59E0B),
                                    enabled = isConnected,
                                    centerContent = true,
                                    onClick = onNavigateToPresenter
                                )
                                BentoCard(
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    title = "Files",
                                    icon = Icons.Default.Folder,
                                    primaryColor = AccentCyan,
                                    accentColor = Color(0xFF10B981),
                                    enabled = true,
                                    centerContent = true,
                                    onClick = onNavigateToFileshare
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // --- NEON ACTION PILLS ---
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        ActionPill(title = "Lock", icon = Icons.Default.Lock, color = Color(0xFFEF4444), onClick = { viewModel.lockWindows() })
                        ActionPill(title = "Sync", icon = Icons.Default.Sync, color = AccentCyan, onClick = {
                            try {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clipData = clipboardManager.primaryClip
                                if (clipData != null && clipData.itemCount > 0) {
                                    val text = clipData.getItemAt(0).text?.toString() ?: ""
                                    if (text.isNotEmpty()) viewModel.sendText(text)
                                }
                            } catch (e: Exception) { }
                        })
                        ActionPill(title = "Shield", icon = Icons.Default.Security, color = AccentPurple, onClick = onNavigateToPasswordManager)
                        ActionPill(title = "Settings", icon = Icons.Default.Settings, color = Color(0xFF64748B), onClick = onNavigateToSettings)
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }

                // --- GLASS LIST: AUTOMATION & AI ---
                item {
                    Text("Intelligence & Tools", color = TextTitleDark, fontSize = 18.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 16.dp))
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(16.dp, RoundedCornerShape(BentoRadiusMd), spotColor = Color.Black)
                            .clip(RoundedCornerShape(BentoRadiusMd))
                            .background(GlassSurface)
                            .border(1.dp, GlassBorder, RoundedCornerShape(BentoRadiusMd))
                    ) {
                        val tools = listOf(
                            Triple("Autonomous Agent", Icons.Default.SmartToy, onNavigateToAgent),
                            Triple("Workflow Automation", Icons.Default.SettingsSuggest, onNavigateToAutomation),
                            Triple("Snippets & Notes", Icons.Default.Description, onNavigateToSnippets),
                            Triple("Task Management", Icons.Default.Checklist, onNavigateToTodo),
                            Triple("System Shortcuts", Icons.Default.Bolt, onNavigateToShortcuts),
                            Triple("Terminal Chat", Icons.AutoMirrored.Filled.Chat, onNavigateToChat)
                        )
                        
                        tools.forEachIndexed { index, (title, icon, onClick) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onClick() }
                                    .padding(horizontal = 24.dp, vertical = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier.size(36.dp).background(AccentPurple.copy(alpha = 0.2f), RoundedCornerShape(10.dp)).border(1.dp, AccentPurple.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon as ImageVector, contentDescription = null, tint = AccentPurple, modifier = Modifier.size(18.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(title as String, color = TextTitleDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSubDark, modifier = Modifier.size(16.dp))
                            }
                            if (index < tools.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = GlassBorder, thickness = 1.dp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }

                // --- SAVED DEVICES ---
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Saved Hardware", color = TextTitleDark, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("+ Add Device", color = AccentCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onNavigateToPairing() }.padding(4.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (savedDevices.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(GlassSurface, RoundedCornerShape(BentoRadiusMd)).border(1.dp, GlassBorder, RoundedCornerShape(BentoRadiusMd)), contentAlignment = Alignment.Center) {
                            Text("No devices paired yet.", color = TextSubDark, fontSize = 14.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(BentoRadiusMd))
                                .background(GlassSurface)
                                .border(1.dp, GlassBorder, RoundedCornerShape(BentoRadiusMd))
                        ) {
                            savedDevices.forEachIndexed { index, device ->
                                val isConn = (deviceName == device.name || deviceName == device.address)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.connectDevice(device.address) }
                                        .padding(horizontal = 24.dp, vertical = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier.size(44.dp).background(if (isConn) AccentCyan.copy(alpha=0.2f) else Color.White.copy(alpha=0.05f), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Laptop, contentDescription = null, tint = if(isConn) AccentCyan else TextSubDark, modifier = Modifier.size(24.dp))
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text(device.name, color = TextTitleDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Text(if (isConn) "Active Connection" else device.address, color = if (isConn) AccentCyan else TextSubDark, fontSize = 13.sp)
                                        }
                                    }
                                    if (isConn) {
                                        Box(modifier = Modifier.size(28.dp).background(AccentCyan, CircleShape).shadow(8.dp, CircleShape, spotColor = AccentCyan), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        }
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSubDark, modifier = Modifier.size(16.dp))
                                    }
                                }
                                if (index < savedDevices.size - 1) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = GlassBorder, thickness = 1.dp)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(40.dp))
                }
            } // end LazyColumn
                
            // TOP BAR (GLASS EFFECT)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(DarkBg.copy(alpha=0.9f), DarkBg.copy(alpha=0.6f), Color.Transparent)))
            ) {
                ModernTopBar(onOpenDrawer, isConnected, AccentCyan, onConnectClick = { if (isConnected) viewModel.disconnect() else onNavigateToPairing() })
            }
        }
    }
}

// ── CUSTOM COMPONENTS ──

@Composable
fun BiDirectionalSwipeUnlock(
    isConnected: Boolean,
    primaryColor: Color,
    onSwipeRight: () -> Unit,
    onSwipeLeft: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(targetValue = offsetX, animationSpec = if (offsetX == 0f) spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow) else tween(0), label = "swipe")
    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .shadow(16.dp, RoundedCornerShape(40.dp), spotColor = Color.Black)
            .clip(RoundedCornerShape(40.dp))
            .background(GlassSurface)
            .border(1.dp, GlassBorder, RoundedCornerShape(40.dp))
            .padding(10.dp)
    ) {
        val maxDrag = with(LocalDensity.current) { (maxWidth.toPx() / 2f) - (60.dp.toPx() / 2f) - 10.dp.toPx() }
        val fadeThreshold = maxDrag * 0.4f
        val leftTextAlpha = if (offsetX > 0f) 0f else (1f - (Math.abs(offsetX) / fadeThreshold)).coerceIn(0f, 1f)
        val rightTextAlpha = if (offsetX < 0f) 0f else (1f - (Math.abs(offsetX) / fadeThreshold)).coerceIn(0f, 1f)

        // Background Text
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("← Wake PC", color = TextSubDark.copy(alpha = leftTextAlpha), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Direct Unlock →", color = TextSubDark.copy(alpha = rightTextAlpha), fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }

        // Draggable Thumb
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                    .size(60.dp)
                    .shadow(16.dp, CircleShape, spotColor = if (isConnected) primaryColor else Color.Black)
                    .background(if (isConnected) primaryColor else Color(0xFF3F3F46), CircleShape)
                    .pointerInput(isConnected) {
                        if (!isConnected) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                            onDragEnd = {
                                if (offsetX > maxDrag * 0.7f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSwipeRight() }
                                else if (offsetX < -maxDrag * 0.7f) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onSwipeLeft() }
                                offsetX = 0f
                            },
                            onDragCancel = { offsetX = 0f },
                            onHorizontalDrag = { change, dragAmount -> change.consume(); offsetX = (offsetX + dragAmount).coerceIn(-maxDrag, maxDrag) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LockOpen, contentDescription = null, tint = if (isConnected) Color.Black else Color.White)
            }
        }
    }
}

@Composable
fun VerticalMediaControlBar(modifier: Modifier, viewModel: CommLinkViewModel, primaryColor: Color) {
    val nestedScrollConnection = remember {
        object : NestedScrollConnection { override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available }
    }
    Column(
        modifier = modifier
            .shadow(16.dp, RoundedCornerShape(BentoRadiusLg), spotColor = Color.Black)
            .background(Color(0xFF000000).copy(alpha=0.6f), RoundedCornerShape(BentoRadiusLg))
            .border(1.dp, GlassBorder, RoundedCornerShape(BentoRadiusLg))
            .padding(vertical = 16.dp)
            .nestedScroll(nestedScrollConnection)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        IconButton(onClick = { viewModel.sendMediaVolumeUp() }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Vol+", tint = TextTitleDark) }
        IconButton(onClick = { viewModel.sendMediaPrev() }) { Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = TextTitleDark) }
        Box(
            modifier = Modifier.size(48.dp).background(primaryColor, CircleShape).shadow(12.dp, CircleShape, spotColor = primaryColor).clickable { viewModel.sendMediaPlayPause() },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(24.dp)) }
        IconButton(onClick = { viewModel.sendMediaNext() }) { Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = TextTitleDark) }
        IconButton(onClick = { viewModel.sendMediaVolumeDown() }) { Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Vol-", tint = TextTitleDark) }
    }
}

@Composable
fun ModernTopBar(onOpenDrawer: () -> Unit, isConnected: Boolean, primaryColor: Color, onConnectClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(GlassSurface, CircleShape).border(1.dp, GlassBorder, CircleShape).clickable { onOpenDrawer() }.clip(CircleShape),
            contentAlignment = Alignment.Center
        ) { Image(painter = painterResource(id = R.drawable.commvault_logo), contentDescription = "Menu", modifier = Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) }
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("COMM", color = TextTitleDark, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
            Text("LINK", color = primaryColor, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        }
        
        Box(
            modifier = Modifier.size(48.dp).background(if (isConnected) primaryColor.copy(alpha=0.2f) else GlassSurface, CircleShape)
                .border(1.dp, if (isConnected) primaryColor.copy(alpha=0.5f) else GlassBorder, CircleShape).clickable { onConnectClick() }.clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (isConnected) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, 
                contentDescription = "Bluetooth", tint = if (isConnected) primaryColor else TextSubDark, modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun BentoCard(modifier: Modifier = Modifier, title: String, icon: ImageVector, primaryColor: Color, accentColor: Color, enabled: Boolean, large: Boolean = false, centerContent: Boolean = false, onClick: () -> Unit) {
    val bg = if (enabled) GlassSurface else GlassSurface.copy(alpha=0.3f)
    val contentColor = if (enabled) TextTitleDark else TextSubDark
    
    Box(
        modifier = modifier
            .shadow(if (enabled) 16.dp else 0.dp, RoundedCornerShape(BentoRadiusLg), spotColor = Color.Black)
            .clip(RoundedCornerShape(BentoRadiusLg))
            .background(bg)
            .border(1.dp, GlassBorder, RoundedCornerShape(BentoRadiusLg))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(if (large) 24.dp else 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(), 
            verticalArrangement = if (centerContent) Arrangement.Center else Arrangement.SpaceBetween,
            horizontalAlignment = if (centerContent) Alignment.CenterHorizontally else Alignment.Start
        ) {
            Box(
                modifier = Modifier.size(if (large) 56.dp else 44.dp).background(if (enabled) accentColor.copy(alpha=0.15f) else Color.White.copy(alpha=0.05f), CircleShape).border(1.dp, if (enabled) accentColor.copy(alpha=0.4f) else Color.Transparent, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = if(enabled) accentColor else TextSubDark, modifier = Modifier.size(if (large) 28.dp else 22.dp)) }
            if (centerContent) Spacer(modifier = Modifier.height(16.dp))
            Text(
                title, color = contentColor, fontSize = if (large) 22.sp else 15.sp, 
                fontWeight = FontWeight.Black, textAlign = if (centerContent) TextAlign.Center else TextAlign.Start, lineHeight = if (large) 26.sp else 20.sp
            )
        }
    }
}

@Composable
fun ActionPill(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            modifier = Modifier.size(64.dp).shadow(12.dp, CircleShape, spotColor = color.copy(alpha=0.5f)).background(GlassSurface, CircleShape).border(1.dp, color.copy(alpha=0.3f), CircleShape).clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(28.dp)) }
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, color = TextTitleDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
