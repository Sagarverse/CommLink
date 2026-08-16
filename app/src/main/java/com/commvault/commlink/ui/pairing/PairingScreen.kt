package com.commvault.commlink.ui.pairing

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.LaptopWindows
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit,
    onConnectSuccess: () -> Unit
) {
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val isConnecting = connectionState is HidDeviceManager.ConnectionState.Connecting

    LaunchedEffect(connectionState) {
        if (isConnected) {
            onConnectSuccess()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            viewModel.startScanning()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        permissionLauncher.launch(permissionsToRequest.toTypedArray())
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopScanning()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Find Workstation", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { 
                            if (isScanning) {
                                viewModel.stopScanning()
                            } else {
                                val permissionsToRequest = mutableListOf<String>()
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                                    permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                                } else {
                                    permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                                    permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                                }
                                permissionLauncher.launch(permissionsToRequest.toTypedArray())
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(if (isScanning) CommvaultPink else LightSurfaceAlt, CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh, 
                            contentDescription = "Scan", 
                            tint = if (isScanning) Color.White else TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
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

            // Connection Progress Indicator
            AnimatedVisibility(visible = isConnecting) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    color = LightSurface,
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 2.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, CommvaultPink)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CommvaultPink,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Establishing L2CAP connection...",
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Text(
                text = if (isScanning) "SCANNING FOR BLUETOOTH DEVICES..." else "SCANNING PAUSED",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (scannedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(LightSurfaceAlt)
                        .border(1.dp, BorderColor, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = CommvaultPink)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching for nearby computers...",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(scannedDevices.toList()) { device ->
                        var isDeviceTargetConnected = false
                        try {
                            isDeviceTargetConnected = isConnected && (connectionState as? HidDeviceManager.ConnectionState.Connected)?.deviceName == device.name
                        } catch (e: SecurityException) {}

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isConnecting) {
                                    viewModel.connect(device)
                                },
                            color = if (isDeviceTargetConnected) LightSurfaceAlt else LightSurface,
                            shape = RoundedCornerShape(16.dp),
                            shadowElevation = 1.dp,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isDeviceTargetConnected) SuccessTeal else BorderColor
                            )
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
                                            .background(LightSurfaceAlt, RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LaptopWindows,
                                            contentDescription = null,
                                            tint = if (isDeviceTargetConnected) SuccessTeal else TextTertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column {
                                        var dName = "Unknown Device"
                                        try {
                                            dName = device.name ?: "Unknown Device"
                                        } catch (e: SecurityException) {}
                                        Text(
                                            text = dName,
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = device.address,
                                            color = TextTertiary,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (isDeviceTargetConnected) {
                                    Text(
                                        text = "CONNECTED",
                                        color = SuccessTeal,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
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
}
