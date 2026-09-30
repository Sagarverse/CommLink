package com.commvault.commlink.agent

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    onBack: () -> Unit,
    agentViewModel: AgentViewModel = viewModel()
) {
    val context = LocalContext.current
    val messages by agentViewModel.messages.collectAsState()
    val isRunning by agentViewModel.isRunning.collectAsState()
    val currentStatus by agentViewModel.currentStatus.collectAsState()
    val isAccessibilityEnabled = agentViewModel.isAccessibilityEnabled(context)

    val primary = LocalPrimaryColor.current
    val agentColor = Color(0xFF7C3AED) // Purple for agent

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Voice input
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull() ?: return@rememberLauncherForActivityResult
            agentViewModel.sendCommand(context, spokenText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isAccessibilityEnabled) Color(0xFF10B981) else Color(0xFFF59E0B),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "AI Agent",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CommvaultNavy
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = agentColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "AUTONOMOUS",
                                    color = agentColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            if (isAccessibilityEnabled) "Ready to control your phone"
                            else "⚠️ Enable Accessibility to activate",
                            fontSize = 11.sp,
                            color = if (isAccessibilityEnabled) TextSecondary else Color(0xFFF59E0B)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CommvaultNavy)
                    }
                },
                actions = {
                    IconButton(onClick = { agentViewModel.clearMessages() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
            )
        },
        containerColor = Color(0xFFF5F3FF) // Soft purple tint bg
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Accessibility banner if not enabled
            AnimatedVisibility(!isAccessibilityEnabled) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFEF3C7)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Accessibility Service needed to control your phone.",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { agentViewModel.openAccessibilitySettings(context) }) {
                            Text("Enable", color = agentColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Chat area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                // Welcome message
                if (messages.isEmpty()) {
                    item {
                        AgentWelcomeCard(agentColor = agentColor, onExampleClick = { example ->
                            agentViewModel.sendCommand(context, example)
                        })
                    }
                }

                items(messages) { msg ->
                    AgentMessageBubble(message = msg, agentColor = agentColor)
                }

                if (isRunning) {
                    item { AgentThinkingBubble(agentColor) }
                }
            }

            // Quick command suggestions
            QuickAgentCommands(
                agentColor = agentColor,
                onCommandClick = { agentViewModel.sendCommand(context, it) }
            )

            // Input bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightSurface,
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice button
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Tell agent what to do...")
                                }
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {}
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(agentColor.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice", tint = agentColor)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tell agent what to do...", fontSize = 14.sp, color = TextTertiary) },
                        maxLines = 3,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = agentColor,
                            unfocusedBorderColor = agentColor.copy(alpha = 0.3f),
                            focusedContainerColor = Color(0xFFF5F3FF),
                            unfocusedContainerColor = Color(0xFFF5F3FF)
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                agentViewModel.sendCommand(context, inputText)
                                inputText = ""
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                agentViewModel.sendCommand(context, inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !isRunning,
                        modifier = Modifier
                            .size(46.dp)
                            .background(
                                if (inputText.isNotBlank()) agentColor else Color.LightGray,
                                CircleShape
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AgentWelcomeCard(agentColor: Color, onExampleClick: (String) -> Unit) {
    val examples = listOf(
        "📱 Open WhatsApp and send hi to Sanjay",
        "🎵 Open Spotify and play something",
        "📷 Open Camera",
        "⚙️ Open WiFi Settings",
        "📞 Call Mom",
        "🔔 Open my notifications",
        "🏠 Go to Home screen",
        "🔍 Search YouTube for lofi music"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(agentColor.copy(alpha = 0.08f), agentColor.copy(alpha = 0.03f))
                )
            )
            .border(1.dp, agentColor.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(agentColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = agentColor, modifier = Modifier.size(36.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("CommLink AI Agent", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = CommvaultNavy)
        Text(
            "I can control your phone autonomously.\nJust tell me what to do in plain language.",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Try these commands:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CommvaultNavy)
        Spacer(modifier = Modifier.height(8.dp))

        examples.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { example ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onExampleClick(example.substring(2).trim()) },
                        shape = RoundedCornerShape(10.dp),
                        color = LightSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, agentColor.copy(alpha = 0.25f))
                    ) {
                        Text(
                            example,
                            fontSize = 11.sp,
                            color = CommvaultNavy,
                            modifier = Modifier.padding(8.dp),
                            lineHeight = 15.sp
                        )
                    }
                }
                // Fill empty slot if odd row
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun AgentMessageBubble(message: AgentMessage, agentColor: Color) {
    val isUser = message.isUser
    val isStatus = message.isStatus

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        if (isStatus) Color(0xFF10B981).copy(alpha = 0.15f) else agentColor.copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isStatus) Icons.Default.Check else Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = if (isStatus) Color(0xFF10B981) else agentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp, topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            color = when {
                isUser -> agentColor
                isStatus -> Color(0xFFECFDF5)
                else -> LightSurface
            },
            border = if (!isUser) androidx.compose.foundation.BorderStroke(
                1.dp,
                if (isStatus) Color(0xFF10B981).copy(alpha = 0.3f) else agentColor.copy(alpha = 0.15f)
            ) else null,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                fontSize = 13.sp,
                color = when {
                    isUser -> Color.White
                    isStatus -> Color(0xFF065F46)
                    else -> CommvaultNavy
                },
                modifier = Modifier.padding(12.dp),
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun AgentThinkingBubble(agentColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "agent_think")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .background(agentColor.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.SmartToy, contentDescription = null, tint = agentColor, modifier = Modifier.size(16.dp))
        }
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = LightSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, agentColor.copy(alpha = 0.2f))
        ) {
            Text(
                "⚡ Executing...",
                fontSize = 13.sp,
                color = agentColor.copy(alpha = alpha),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
fun QuickAgentCommands(agentColor: Color, onCommandClick: (String) -> Unit) {
    val commands = listOf(
        "🏠 Go Home",
        "🔔 Notifications",
        "⚙️ Settings",
        "🔙 Go Back",
        "📸 Screenshot"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        commands.forEach { cmd ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = LightSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, agentColor.copy(alpha = 0.25f)),
                modifier = Modifier.clickable { onCommandClick(cmd.substring(2).trim()) }
            ) {
                Text(
                    cmd,
                    fontSize = 12.sp,
                    color = CommvaultNavy,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                )
            }
        }
    }
}
