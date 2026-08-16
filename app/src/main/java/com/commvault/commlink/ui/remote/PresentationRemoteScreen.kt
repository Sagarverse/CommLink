package com.commvault.commlink.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import com.commvault.commlink.ui.components.MacroControlBar
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.domain.model.HidKeyCodes
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresentationRemoteScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Presenter Remote", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Connection banner warning
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
                                text = "Workstation disconnected. Slides controller disabled.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }



            // Controller Surface Card
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                color = LightSurface,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 2.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround
                ) {
                    Text(
                        text = "SLIDES NAVIGATION",
                        color = TextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    // Big Navigation buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Previous button
                        PresenterLargeButton(
                            modifier = Modifier.fillMaxWidth(0.85f),
                            title = "PREVIOUS SLIDE",
                            icon = Icons.AutoMirrored.Filled.NavigateBefore,
                            enabled = isConnected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                viewModel.sendKey(HidKeyCodes.KEY_LEFT)
                            }
                        )

                        // Next button
                        PresenterLargeButton(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(120.dp),
                            title = "NEXT SLIDE",
                            icon = Icons.AutoMirrored.Filled.NavigateNext,
                            accentColor = CommvaultPink,
                            enabled = isConnected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.sendKey(HidKeyCodes.KEY_RIGHT)
                            }
                        )
                    }

                    // Bottom controls (F5, Esc, B)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PresenterSmallUtilityButton(
                            modifier = Modifier.weight(1f),
                            title = "Start (F5)",
                            icon = Icons.Default.PlayArrow,
                            enabled = isConnected,
                            onClick = {
                                viewModel.sendKey(HidKeyCodes.KEY_F5)
                            }
                        )
                        PresenterSmallUtilityButton(
                            modifier = Modifier.weight(1f),
                            title = "Exit (Esc)",
                            icon = Icons.Default.Close,
                            enabled = isConnected,
                            onClick = {
                                viewModel.sendKey(HidKeyCodes.KEY_ESC)
                            }
                        )
                        PresenterSmallUtilityButton(
                            modifier = Modifier.weight(1f),
                            title = "Blackout (B)",
                            icon = Icons.Default.VisibilityOff,
                            enabled = isConnected,
                            onClick = {
                                viewModel.sendKey(HidKeyCodes.getHidCode('b').keyCode)
                            }
                        )
                    }
                }
        }
    }
}
}
}

@Composable
fun PresenterLargeButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    accentColor: Color = LightSurfaceAlt,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(80.dp)
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) accentColor else accentColor.copy(alpha = 0.3f),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (enabled) 1.dp else 0.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) BorderColor else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (accentColor == CommvaultPink && enabled) Color.White
                       else if (enabled) TextPrimary else TextTertiary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = if (accentColor == CommvaultPink && enabled) Color.White
                       else if (enabled) TextPrimary else TextTertiary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
fun PresenterSmallUtilityButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(60.dp)
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) LightSurfaceAlt else LightSurfaceAlt.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) BorderColor else BorderColor.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) TextSecondary else TextTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = if (enabled) TextSecondary else TextTertiary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
