package com.commvault.commlink.ui.automation

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import androidx.compose.ui.draw.shadow

// ── ULTRA MODERN UI CONSTANTS ──
private val PageBg = Color(0xFFF4F7FB)
private val SurfaceCard = Color(0xFFFFFFFF)
private val SurfaceCardLight = Color(0xFFF8FAFC)
private val TextTitle = Color(0xFF0F172A)
private val TextSub = Color(0xFF64748B)
private val BentoRadiusLg = 32.dp
private val BentoRadiusMd = 24.dp
private val BentoRadiusSm = 16.dp
private val ShadowColor = Color(0xFF334155).copy(alpha = 0.08f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected

    val isKeepAliveActive by viewModel.isKeepAliveActive.collectAsState()
    val isMacroRunning by viewModel.isMacroRunning.collectAsState()

    val prefs = context.getSharedPreferences("commlink_prefs", Context.MODE_PRIVATE)
    var customScript by remember { mutableStateOf(prefs.getString("saved_custom_macro", "") ?: "") }
    
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automation Hub", color = TextTitle, fontWeight = FontWeight.Black, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextTitle)
                    }
                },
                actions = {
                    // MacroControlBar(viewModel = viewModel)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBg)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PageBg)
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Connection warning banner
                if (!isConnected) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                        color = ErrorRed.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorRed.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = ErrorRed)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Workstation disconnected. Automation keys disabled.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // Keep Alive Status
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .shadow(4.dp, RoundedCornerShape(BentoRadiusMd), spotColor = ShadowColor)
                        .clip(RoundedCornerShape(BentoRadiusMd))
                        .background(SurfaceCard)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (isKeepAliveActive) Color(0xFF10B981).copy(alpha = 0.15f) else SurfaceCardLight,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bedtime, 
                                    contentDescription = null, 
                                    tint = if (isKeepAliveActive) Color(0xFF10B981) else TextSub,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column {
                                Text(
                                    text = "Anti-Sleep",
                                    color = TextTitle,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isKeepAliveActive) "Jiggling mouse..." else "Disabled",
                                    color = if (isKeepAliveActive) Color(0xFF10B981) else TextSub,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.weight(1f))
                        
                        Switch(
                            checked = isKeepAliveActive,
                            onCheckedChange = { viewModel.toggleKeepAlive() },
                            enabled = isConnected,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981)
                            )
                        )
                    }
                }

                // System Actions Header
                Text(
                    text = "SYSTEM COMMANDS",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Minimize & Lock", subtitle = "Hide all & lock", icon = Icons.Default.Lock,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI d\nDELAY 200\nGUI l") }
                        )
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Clear Desktop", subtitle = "Minimize everything", icon = Icons.Default.DesktopWindows,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI d") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Task Manager", subtitle = "Ctrl+Shift+Esc", icon = Icons.Default.Memory,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("CTRL SHIFT ESC") }
                        )
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Lock Screen", subtitle = "Win+L directly", icon = Icons.Default.VpnKey,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI l") }
                        )
                    }
                }

                // Productivity Apps
                Text(
                    text = "QUICK APPS",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Terminal", subtitle = "cmd.exe", icon = Icons.Default.Terminal,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nENTER") }
                        )
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "VS Code", subtitle = "code", icon = Icons.Default.Code,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING code\nDELAY 200\nENTER") }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Notepad", subtitle = "Quick text", icon = Icons.Default.EditNote,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING notepad\nDELAY 200\nENTER") }
                        )
                        MacroButton(
                            modifier = Modifier.weight(1f),
                            title = "Incognito Web", subtitle = "Private browser", icon = Icons.Default.Public,
                            enabled = isConnected && !isMacroRunning,
                            onClick = { viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING chrome --incognito\nDELAY 200\nENTER") }
                        )
                    }
                }

                // Custom Script Area
                Text(
                    text = "CUSTOM MACRO SEQUENCE",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(BentoRadiusMd), spotColor = ShadowColor)
                        .clip(RoundedCornerShape(BentoRadiusMd))
                        .background(SurfaceCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Script Editor (DuckyScript Format)",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            if (customScript.isNotBlank()) {
                                Text(
                                    text = "Auto-saved",
                                    color = Color(0xFF10B981),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        OutlinedTextField(
                            value = customScript,
                            onValueChange = { 
                                customScript = it
                                prefs.edit().putString("saved_custom_macro", it).apply()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            placeholder = { 
                                Text(
                                    "Commands:\nGUI, SHIFT, ALT, CTRL, ENTER\nDELAY 500\nSTRING Hello World",
                                    color = TextTertiary,
                                    fontSize = 12.sp
                                ) 
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF3B82F6),
                                unfocusedBorderColor = SurfaceCardLight,
                                focusedTextColor = TextTitle,
                                unfocusedTextColor = TextTitle
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.runMacroScript(customScript) },
                            enabled = isConnected && !isMacroRunning && customScript.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
                        ) {
                            if (isMacroRunning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Execute Sequence", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }
}

@Composable
fun MacroButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 90.dp)
            .shadow(4.dp, RoundedCornerShape(BentoRadiusMd), spotColor = ShadowColor)
            .clip(RoundedCornerShape(BentoRadiusMd))
            .background(SurfaceCard)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (enabled) Color(0xFF3B82F6).copy(alpha = 0.12f) else SurfaceCardLight,
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF3B82F6) else TextSub.copy(alpha = 0.3f),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = if (enabled) TextTitle else TextSub,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextSub,
                    fontSize = 12.sp
                )
            }
        }
    }
}
