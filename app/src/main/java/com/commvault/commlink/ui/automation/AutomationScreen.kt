package com.commvault.commlink.ui.automation

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.components.MacroControlBar
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected

    val isKeepAliveActive by viewModel.isKeepAliveActive.collectAsState()
    val isMacroRunning by viewModel.isMacroRunning.collectAsState()
    val isMacroPaused by viewModel.isMacroPaused.collectAsState()

    var customScript by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Automation Hub", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    MacroControlBar(viewModel = viewModel)
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
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                color = LightSurface,
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (isKeepAliveActive) SuccessTeal.copy(alpha = 0.1f) else LightSurfaceAlt,
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bedtime, 
                                contentDescription = null, 
                                tint = if (isKeepAliveActive) SuccessTeal else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = "Anti-Sleep / Keep-Alive",
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isKeepAliveActive) "Simulating user activity..." else "Disabled",
                                color = if (isKeepAliveActive) SuccessTeal else TextTertiary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    
                    Switch(
                        checked = isKeepAliveActive,
                        onCheckedChange = { viewModel.toggleKeepAlive() },
                        enabled = isConnected,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SuccessTeal,
                            checkedTrackColor = SuccessTeal.copy(alpha = 0.3f)
                        )
                    )
                }
            }

            // Quick Office Macros Header
            Text(
                text = "QUICK OFFICE MACROS",
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MacroButton(
                        modifier = Modifier.weight(1f),
                        title = "Minimize & Lock",
                        subtitle = "Minimize all & lock screen",
                        icon = Icons.Default.Lock,
                        enabled = isConnected && !isMacroRunning,
                        onClick = {
                            viewModel.runMacroScript("GUI d\nDELAY 200\nGUI l")
                        }
                    )
                    MacroButton(
                        modifier = Modifier.weight(1f),
                        title = "Open Notepad",
                        subtitle = "Launch text editor",
                        icon = Icons.Default.EditNote,
                        enabled = isConnected && !isMacroRunning,
                        onClick = {
                            viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING notepad\nDELAY 200\nENTER")
                        }
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    MacroButton(
                        modifier = Modifier.weight(1f),
                        title = "Open Terminal",
                        subtitle = "Open cmd prompt",
                        icon = Icons.Default.Terminal,
                        enabled = isConnected && !isMacroRunning,
                        onClick = {
                            viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING cmd\nDELAY 200\nENTER")
                        }
                    )
                    MacroButton(
                        modifier = Modifier.weight(1f),
                        title = "Launch Outlook",
                        subtitle = "Start mail client",
                        icon = Icons.Default.Email,
                        enabled = isConnected && !isMacroRunning,
                        onClick = {
                            viewModel.runMacroScript("GUI r\nDELAY 500\nSTRING outlook\nDELAY 200\nENTER")
                        }
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightSurface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 1.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Script Editor (DuckyScript Format)",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = customScript,
                        onValueChange = { customScript = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        placeholder = { 
                            Text(
                                "Example:\nGUI r\nDELAY 400\nSTRING notepad\nENTER\nDELAY 300\nSTRING Hello from CommLink!",
                                color = TextTertiary,
                                fontSize = 12.sp
                            ) 
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CommvaultPink,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.runMacroScript(customScript) },
                        enabled = isConnected && !isMacroRunning && customScript.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CommvaultPink)
                    ) {
                        if (isMacroRunning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                        } else {
                            Text("Execute Sequence", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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
    Surface(
        modifier = modifier
            .heightIn(min = 85.dp)
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) LightSurface else LightSurfaceAlt,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = if (enabled) 1.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) BorderColor else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
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
            Column {
                Text(
                    text = title,
                    color = if (enabled) TextPrimary else TextTertiary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }
        }
    }
}
