package com.commvault.commlink.ui.audiobridge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commvault.commlink.ui.audiobridge.model.*
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioBridgeScreen(
    onBack: () -> Unit,
    viewModel: AudioBridgeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("AI Audio Bridge", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Text("Route audio manually between this phone and your Windows laptop.", color = TextTertiary, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = PageBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionStatus(uiState)
            SectionTransport(uiState, viewModel)
            SectionLaptopToPhone(uiState, viewModel)
            SectionPhoneToLaptop(uiState, viewModel)
            SectionMasterControls(uiState, viewModel)
            SectionAdvancedAudio(uiState, viewModel)
        }
        
        uiState.recoverableError?.let { error ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissError() },
                title = { Text(error.title, color = ErrorRed, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(error.message)
                        error.technicalDetails?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Details: $it", fontSize = 12.sp, color = TextTertiary)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss", color = com.commvault.commlink.ui.theme.LocalPrimaryColor.current) }
                }
            )
        }
    }
}

@Composable
private fun SectionStatus(uiState: AudioBridgeUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("OVERALL STATUS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            StatusRow(icon = Icons.Default.AdminPanelSettings, label = "Root Status", value = uiState.rootState.javaClass.simpleName)
            StatusRow(icon = Icons.Default.Usb, label = "Transport", value = uiState.selectedTransport.name)
            StatusRow(icon = Icons.Default.ArrowDownward, label = "Laptop → Phone", value = uiState.laptopToPhoneState.javaClass.simpleName, isActive = uiState.laptopToPhoneState == RouteState.Active)
            StatusRow(icon = Icons.Default.ArrowUpward, label = "Phone → Laptop", value = uiState.phoneToLaptopState.javaClass.simpleName, isActive = uiState.phoneToLaptopState == RouteState.Active)
            StatusRow(icon = Icons.Default.Mic, label = "Virtual Mic", value = if (uiState.laptopToPhoneState == RouteState.Active) "Injected" else "Idle")
            
            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = BorderLight)
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${uiState.audioConfiguration.sampleRate} Hz • ${if(uiState.audioConfiguration.stereo) "Stereo" else "Mono"}", fontSize = 12.sp, color = TextSecondary)
                Text("Active: ${uiState.activeDurationMs / 1000}s", fontSize = 12.sp, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun StatusRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, isActive: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = TextSecondary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, fontSize = 14.sp, color = TextSecondary)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isActive) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else TextPrimary)
    }
}

@Composable
private fun SectionTransport(uiState: AudioBridgeUiState, viewModel: AudioBridgeViewModel) {
    Column {
        Text("TRANSPORT SELECTION", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransportCard(
                modifier = Modifier.weight(1f),
                title = "USB",
                subtitle = "Recommended\nLow-latency digital audio using Android USB Audio Gadget mode.",
                isSelected = uiState.selectedTransport == Transport.USB,
                onClick = { viewModel.selectTransport(Transport.USB) }
            )
            TransportCard(
                modifier = Modifier.weight(1f),
                title = "Bluetooth",
                subtitle = "Experimental\nWireless routing when compatible audio roles are available.",
                isSelected = uiState.selectedTransport == Transport.BLUETOOTH,
                onClick = { viewModel.selectTransport(Transport.BLUETOOTH) }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val context = androidx.compose.ui.platform.LocalContext.current
        Button(
            onClick = { viewModel.scanCapabilities(context) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceAlt, contentColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)
        ) {
            Text("Scan Capabilities", fontWeight = FontWeight.Bold)
        }
        
        if (uiState.transportCapability.usb != CapabilityResult.Unchecked) {
            Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = CardSurface)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Capability Report", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("USB: ${uiState.transportCapability.usb.javaClass.simpleName}", fontSize = 12.sp)
                    Text("Bluetooth: ${uiState.transportCapability.bluetooth.javaClass.simpleName}", fontSize = 12.sp)
                    if (uiState.transportCapability.diagnostics.isNotEmpty()) {
                        Text(uiState.transportCapability.diagnostics, fontSize = 10.sp, color = TextTertiary, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransportCard(modifier: Modifier, title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = if (isSelected) com.commvault.commlink.ui.theme.LocalPrimaryColor.current.copy(alpha = 0.1f) else CardSurface),
        border = BorderStroke(1.dp, if (isSelected) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else BorderLight)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = if (isSelected) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(subtitle, fontSize = 11.sp, color = TextSecondary, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun SectionLaptopToPhone(uiState: AudioBridgeUiState, viewModel: AudioBridgeViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Laptop → Phone AI", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Receive laptop output and route it to the phone AI microphone path.", fontSize = 12.sp, color = TextSecondary)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Virtual Microphone Method", fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
            VirtualMicMethod.values().forEach { method ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { viewModel.selectVirtualMicMethod(method) }.padding(vertical = 4.dp)) {
                    RadioButton(
                        selected = uiState.selectedVirtualMicMethod == method,
                        onClick = { viewModel.selectVirtualMicMethod(method) },
                        colors = RadioButtonDefaults.colors(selectedColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current, unselectedColor = TextSecondary)
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(method.label, fontSize = 14.sp, color = TextPrimary)
                        Text(method.description, fontSize = 11.sp, color = TextTertiary, lineHeight = 14.sp)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.laptopToPhoneState == RouteState.Active) {
                    Button(onClick = { viewModel.stopLaptopToPhone(context) }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), modifier = Modifier.weight(1f)) {
                        Text("Stop Route")
                    }
                } else {
                    Button(onClick = { viewModel.startLaptopToPhone(context) }, colors = ButtonDefaults.buttonColors(containerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current), modifier = Modifier.weight(1f)) {
                        Text("Start Route")
                    }
                }
                
                Button(
                    onClick = { viewModel.setLaptopToPhoneMute(!uiState.laptopToPhoneMuted) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.laptopToPhoneMuted) WarningAmber else CardSurfaceAlt, contentColor = if (uiState.laptopToPhoneMuted) Color.White else TextPrimary)
                ) {
                    Icon(if (uiState.laptopToPhoneMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = null)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            var appDropdownExpanded by remember { mutableStateOf(false) }
            Text("Target App", fontSize = 12.sp, color = TextSecondary)
            Box {
                Button(
                    onClick = { appDropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceAlt, contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(uiState.selectedTargetPackage ?: "Select Target App...")
                }
                DropdownMenu(expanded = appDropdownExpanded, onDismissRequest = { appDropdownExpanded = false }) {
                    listOf("com.openai.chatgpt", "com.google.android.apps.bard", "com.anthropic.claude").forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(pkg) },
                            onClick = {
                                viewModel.selectTargetPackage(pkg)
                                appDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            LinearProgressIndicator(progress = uiState.incomingLevel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)
        }
    }
}

@Composable
private fun SectionPhoneToLaptop(uiState: AudioBridgeUiState, viewModel: AudioBridgeViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Phone AI → Laptop", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text("Send the selected phone audio source to Windows as microphone input.", fontSize = 12.sp, color = TextSecondary)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.phoneToLaptopState == RouteState.Active) {
                    Button(onClick = { viewModel.stopPhoneToLaptop(context) }, colors = ButtonDefaults.buttonColors(containerColor = ErrorRed), modifier = Modifier.weight(1f)) {
                        Text("Stop Route")
                    }
                } else {
                    Button(onClick = { viewModel.startPhoneToLaptop(context) }, colors = ButtonDefaults.buttonColors(containerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current), modifier = Modifier.weight(1f)) {
                        Text("Start Route")
                    }
                }
                
                Button(
                    onClick = { viewModel.setPhoneToLaptopMute(!uiState.phoneToLaptopMuted) },
                    colors = ButtonDefaults.buttonColors(containerColor = if (uiState.phoneToLaptopMuted) WarningAmber else CardSurfaceAlt, contentColor = if (uiState.phoneToLaptopMuted) Color.White else TextPrimary)
                ) {
                    Icon(if (uiState.phoneToLaptopMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            var sourceDropdownExpanded by remember { mutableStateOf(false) }
            Text("Capture Source", fontSize = 12.sp, color = TextSecondary)
            Box {
                Button(
                    onClick = { sourceDropdownExpanded = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceAlt, contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(uiState.selectedCaptureSource?.label ?: "Select Capture Source...")
                }
                DropdownMenu(expanded = sourceDropdownExpanded, onDismissRequest = { sourceDropdownExpanded = false }) {
                    CaptureSource.values().forEach { source ->
                        DropdownMenuItem(
                            text = { Text(source.label) },
                            onClick = {
                                viewModel.selectCaptureSource(source)
                                sourceDropdownExpanded = false
                            }
                        )
                    }
                }
            }
            LinearProgressIndicator(progress = uiState.outgoingLevel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)
        }
    }
}

@Composable
private fun SectionMasterControls(uiState: AudioBridgeUiState, viewModel: AudioBridgeViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column {
        Text("MASTER MANUAL CONTROLS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.sp, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { viewModel.startBothRoutes(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)) {
                Text("Start Both")
            }
            Button(onClick = { viewModel.stopAllRoutes(context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = TextSecondary)) {
                Text("Stop All")
            }
        }
        
        Button(
            onClick = { viewModel.emergencyCutOff(context) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text("EMERGENCY AUDIO CUT-OFF", fontWeight = FontWeight.Bold)
        }
        
        if (uiState.emergencyStopped) {
            Button(
                onClick = { viewModel.restoreNormalAudio() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CardSurfaceAlt, contentColor = TextPrimary)
            ) {
                Text("Restore Normal Audio")
            }
        }
    }
}

@Composable
private fun SectionAdvancedAudio(uiState: AudioBridgeUiState, viewModel: AudioBridgeViewModel) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, BorderLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Advanced Audio Settings", fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Sample Rate: ${uiState.audioConfiguration.sampleRate} Hz", fontSize = 14.sp)
                    Text("UAC Version: UAC${uiState.audioConfiguration.uacVersion}", fontSize = 14.sp)
                    Text("Buffer Size: ${uiState.audioConfiguration.bufferSize}", fontSize = 14.sp)
                    Text("Keep ADB: ${uiState.audioConfiguration.keepAdb}", fontSize = 14.sp)
                }
            }
        }
    }
}
