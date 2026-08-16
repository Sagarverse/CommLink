package com.commvault.commlink.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.domain.model.HidKeyCodes
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardScreen(
    viewModel: CommLinkViewModel,
    onDisconnect: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val connectionState by viewModel.connectionState.collectAsState()
    var wasConnected by remember { mutableStateOf(false) }

    LaunchedEffect(connectionState) {
        when (connectionState) {
            is HidDeviceManager.ConnectionState.Connected -> {
                wasConnected = true
            }
            is HidDeviceManager.ConnectionState.Disconnected -> {
                if (wasConnected) {
                    wasConnected = false
                    onDisconnect()
                }
            }
            else -> Unit
        }
    }

    // Toggle mouse lock state based on selected tab
    LaunchedEffect(selectedTab) {
        if (selectedTab != 1) {
            viewModel.setMouseLocked(true)
        } else {
            viewModel.setMouseLocked(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.setMouseLocked(true)
        }
    }

    val isTextPushing by viewModel.isTextPushing.collectAsState()
    val deviceName = (connectionState as? HidDeviceManager.ConnectionState.Connected)?.deviceName ?: "Workstation"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(
                            text = deviceName,
                            color = CommvaultNavy,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isTextPushing) "Sending text..." else "Connected",
                            color = if (isTextPushing) CommvaultPink else SuccessTeal,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDisconnect) {
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {

            // Tab Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(LightSurfaceAlt)
                    .padding(4.dp),
            ) {
                val tabs = listOf("Input", "Trackpad")
                tabs.forEachIndexed { index, label ->
                    val active = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MaterialTheme.shapes.medium)
                            .background(if (active) CommvaultPink else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) Color.White else TextSecondary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> InputTab(viewModel)
                    1 -> TrackpadSection(viewModel)
                }
            }
        }
    }
}
}

@Composable
fun InputTab(viewModel: CommLinkViewModel) {
    var isSystemKeyboardMode by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Mode toggle row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(LightSurfaceAlt)
                .padding(4.dp),
        ) {
            val modes = listOf("Remote Keyboard" to false, "System Keyboard" to true)
            modes.forEach { (label, isSystem) ->
                val active = isSystemKeyboardMode == isSystem
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(if (active) LightSurface else Color.Transparent)
                        .clickable { isSystemKeyboardMode = isSystem }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label, 
                        style = MaterialTheme.typography.labelLarge, 
                        color = if (active) TextPrimary else TextSecondary
                    )
                }
            }
        }

        val currentSpeed by viewModel.typingSpeed.collectAsState()
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("SPEED", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            
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
                            .background(if (active) CommvaultPink else LightSurfaceAlt)
                            .clickable { viewModel.setTypingSpeed(speed) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) Color.White else TextPrimary
                        )
                    }
                }
            }
        }

        if (isSystemKeyboardMode) {
            SystemKeyboardInput(viewModel)
        } else {
            val activeModifiers by viewModel.activeModifiers.collectAsState()
            RemoteKeyboardLayout(
                activeModifiers = activeModifiers,
                onModifierClick = { mod -> viewModel.toggleModifier(mod) },
                onKeyPress = { code -> viewModel.sendKey(code) },
            )
        }
    }
}

@Composable
fun SystemKeyboardInput(viewModel: CommLinkViewModel) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var batchText by remember { mutableStateOf("") }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutlinedTextField(
            value = textFieldValue,
            onValueChange = { newValue ->
                val oldText = textFieldValue.text
                val newText = newValue.text

                if (newText.length < oldText.length) {
                    repeat(oldText.length - newText.length) {
                        viewModel.sendKey(HidKeyCodes.KEY_BACKSPACE)
                    }
                } else if (newText.length > oldText.length) {
                    if (newText.startsWith(oldText)) {
                        val diff = newText.substring(oldText.length)
                        viewModel.sendText(diff)
                    } else {
                        // A replacement occurred (e.g. autocorrect), clear and re-type the whole word
                        repeat(oldText.length) { viewModel.sendKey(HidKeyCodes.KEY_BACKSPACE) }
                        viewModel.sendText(newText)
                    }
                }
                textFieldValue = newValue
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            placeholder = { Text("Start typing on your phone keyboard...", color = TextTertiary) },
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CommvaultPink,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "SEND LONG TEXT (BATCH)",
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = batchText,
            onValueChange = { batchText = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            placeholder = { Text("Paste text here and hit send...", color = TextTertiary) },
            shape = MaterialTheme.shapes.large,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CommvaultPink,
                unfocusedBorderColor = BorderColor,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = { 
                    textFieldValue = TextFieldValue("")
                    batchText = "" 
                }
            ) {
                Text("Clear", color = TextSecondary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { 
                    viewModel.sendText(batchText)
                    batchText = "" 
                },
                colors = ButtonDefaults.buttonColors(containerColor = CommvaultPink),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Send Text", color = Color.White)
            }
        }
    }
}

@Composable
fun RemoteKeyboardLayout(
    activeModifiers: Byte,
    onModifierClick: (Byte) -> Unit,
    onKeyPress: (Byte) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val rows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"),
        listOf("A", "S", "D", "F", "G", "H", "J", "K", "L"),
        listOf("Z", "X", "C", "V", "B", "N", "M", "Bksp"),
        listOf("Ctrl", "Alt", "Win", "Space", "Enter")
    )

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp), 
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { label ->
                    val code = when (label) {
                        "Bksp" -> HidKeyCodes.KEY_BACKSPACE
                        "Space" -> HidKeyCodes.KEY_SPACE
                        "Enter" -> HidKeyCodes.KEY_ENTER
                        "Ctrl" -> HidKeyCodes.MODIFIER_LEFT_CTRL
                        "Alt" -> HidKeyCodes.MODIFIER_LEFT_ALT
                        "Win" -> HidKeyCodes.MODIFIER_LEFT_GUI
                        else -> {
                            val char = label[0].lowercaseChar()
                            HidKeyCodes.getHidCode(char).keyCode
                        }
                    }
                    val isMod = label in listOf("Ctrl", "Alt", "Win")
                    val isSelected = isMod && ((activeModifiers.toInt() and code.toInt()) != 0)

                    PremiumKey(
                        label = label,
                        modifier = Modifier.weight(
                            when (label) {
                                "Space" -> 2.4f
                                "Bksp", "Enter" -> 1.4f
                                "Ctrl", "Alt", "Win" -> 1.2f
                                else -> 1f
                            }
                        ),
                        accent = if (isSelected) CommvaultPink else if (isMod) TextSecondary else TextPrimary,
                        onPress = {
                            if (isMod) onModifierClick(code) else onKeyPress(code)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                    )
                }
            }
        }
    }
}
