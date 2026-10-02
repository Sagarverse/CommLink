package com.commvault.commlink.ui.assistant

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.commvault.commlink.ui.components.MarkdownContent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.commvault.commlink.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Gpt4AllAssistantScreen(
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: Gpt4AllViewModel = viewModel()
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val primary = LocalPrimaryColor.current

    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isTtsEnabled by viewModel.isTtsEnabled.collectAsState()
    val isLocalPcMode by viewModel.isLocalPcMode.collectAsState()
    val serverHost by viewModel.serverHost.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var showSettingsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Speech to text launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenMatches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!spokenMatches.isNullOrEmpty()) {
                val query = spokenMatches[0]
                inputText = query
                viewModel.sendMessage(query)
                inputText = ""
            }
        }
    }

    // Document picker launcher
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val fileName = DocumentReader.getFileName(context, uri)
                val fileContent = DocumentReader.readTextFromUri(context, uri)
                if (fileContent.isNotEmpty()) {
                    val prompt = "Here is the content of the document '$fileName':\n\n$fileContent\n\nPlease analyze this document."
                    inputText = prompt
                    Toast.makeText(context, "Document attached", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not read document", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(primary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = primary, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Copilot Assistant",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = CommvaultNavy
                            )
                            Text(
                                text = if (isLocalPcMode) "Local PC Mode" else "Unlimited Free AI",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CommvaultNavy)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleTts() }) {
                        Icon(
                            imageVector = if (isTtsEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                            contentDescription = "Toggle Audio",
                            tint = if (isTtsEnabled) primary else Color.Gray
                        )
                    }
                    IconButton(onClick = { viewModel.clearChat() }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = Color.Gray)
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Tune, contentDescription = "Settings", tint = CommvaultNavy)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightSurface
                )
            )
        },
        containerColor = LightBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Chat message list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.content.isNotEmpty() || msg.role == "user") {
                        ChatBubble(
                            message = msg,
                            primaryColor = primary,
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(msg.content))
                                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            onTypeToPc = {
                                viewModel.typeToPc(msg.content)
                                Toast.makeText(context, "Typing response to PC...", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }

                if (isLoading) {
                    item {
                        ThinkingBubble(primaryColor = primary)
                    }
                }
            }

            // Quick Prompt Suggestions Carousel
            QuickPromptCarousel(
                onPromptSelected = { prompt ->
                    inputText = prompt
                    viewModel.sendMessage(prompt)
                    inputText = ""
                },
                primaryColor = primary
            )

            // Bottom Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightSurface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Voice input mic
                    IconButton(
                        onClick = {
                            try {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask Assistant...")
                                }
                                speechLauncher.launch(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Speech recognizer unavailable", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = TextSecondary)
                    }

                    // Attach Document
                    IconButton(
                        onClick = {
                            documentLauncher.launch(arrayOf("*/*"))
                        }
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach Document", tint = TextSecondary)
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Text input
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask anything...", fontSize = 14.sp, color = TextTertiary) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary,
                            unfocusedBorderColor = BorderColor,
                            focusedContainerColor = CardSurfaceAlt,
                            unfocusedContainerColor = CardSurfaceAlt
                        ),
                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            if (inputText.isNotBlank()) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send or Stop button
                    if (isLoading) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            enabled = inputText.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .background(if (inputText.isNotBlank()) primary else Color.LightGray, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AssistantSettingsDialog(
            isLocalPcMode = isLocalPcMode,
            currentHost = serverHost,
            currentPort = serverPort,
            currentSystemPrompt = systemPrompt,
            primaryColor = primary,
            onDismiss = { showSettingsDialog = false },
            onSave = { localMode, host, port, prompt ->
                viewModel.toggleLocalPcMode(localMode)
                viewModel.updatePcSettings(host, port, prompt)
                showSettingsDialog = false
            }
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    primaryColor: Color,
    onCopy: () -> Unit,
    onTypeToPc: () -> Unit
) {
    val isUser = message.role == "user"
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeStr = remember(message.timestamp) { timeFormat.format(Date(message.timestamp)) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = if (isUser) 290.dp else 340.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) primaryColor else if (message.isError) Color(0xFFFEE2E2) else CardSurface,
                shadowElevation = if (isUser) 2.dp else 1.dp,
                border = if (!isUser && !message.isError) androidx.compose.foundation.BorderStroke(1.dp, BorderColor) else null
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    MarkdownContent(
                        content = message.content,
                        textColor = if (isUser) Color.White else if (message.isError) Color(0xFF991B1B) else CommvaultNavy,
                        primaryColor = primaryColor
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = timeStr,
                        fontSize = 10.sp,
                        color = if (isUser) Color.White.copy(alpha = 0.7f) else TextTertiary,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            // Action buttons below AI response
            if (!isUser && !message.isError && message.content.isNotBlank()) {
                val context = LocalContext.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistChip(
                        onClick = onCopy,
                        label = { Text("Copy", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = CardSurface)
                    )
                    AssistChip(
                        onClick = { DocumentExporter.exportToPdf(context, "CommLink Document", message.content) },
                        label = { Text("PDF", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = CardSurface)
                    )
                    AssistChip(
                        onClick = onTypeToPc,
                        label = { Text("Type to PC", fontSize = 11.sp, color = primaryColor) },
                        leadingIcon = { Icon(Icons.Default.Keyboard, contentDescription = null, tint = primaryColor, modifier = Modifier.size(14.dp)) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = primaryColor.copy(alpha = 0.08f))
                    )
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble(primaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Thinking...",
                    fontSize = 13.sp,
                    color = CommvaultNavy.copy(alpha = alpha),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun QuickPromptCarousel(
    onPromptSelected: (String) -> Unit,
    primaryColor: Color
) {
    val prompts = listOf(
        "🎨 Generate an image of a cybernetic tiger",
        "🎨 Draw a futuristic neon city",
        "💡 Explain code",
        "📝 Summarize notes",
        "✉️ Draft email reply",
        "💻 PowerShell command",
        "🔍 Debug an error"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        prompts.forEach { prompt ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = CardSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, primaryColor.copy(alpha = 0.2f)),
                modifier = Modifier.clickable { onPromptSelected(prompt.substring(3)) }
            ) {
                Text(
                    text = prompt,
                    fontSize = 12.sp,
                    color = CommvaultNavy,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun AssistantSettingsDialog(
    isLocalPcMode: Boolean,
    currentHost: String,
    currentPort: Int,
    currentSystemPrompt: String,
    primaryColor: Color,
    onDismiss: () -> Unit,
    onSave: (localMode: Boolean, host: String, port: Int, prompt: String) -> Unit
) {
    var localMode by remember { mutableStateOf(isLocalPcMode) }
    var host by remember { mutableStateOf(currentHost) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var systemPrompt by remember { mutableStateOf(currentSystemPrompt) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = primaryColor)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Assistant Mode", fontWeight = FontWeight.Bold, color = CommvaultNavy, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (!localMode) primaryColor.copy(alpha = 0.12f) else CardSurfaceAlt,
                    border = if (!localMode) androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localMode = false }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !localMode,
                            onClick = { localMode = false },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Free Cloud AI (Recommended)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CommvaultNavy)
                            Text("100% Free, Unlimited, No API Key, No PC needed.", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (localMode) primaryColor.copy(alpha = 0.12f) else CardSurfaceAlt,
                    border = if (localMode) androidx.compose.foundation.BorderStroke(1.5.dp, primaryColor) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { localMode = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = localMode,
                            onClick = { localMode = true },
                            colors = RadioButtonDefaults.colors(selectedColor = primaryColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text("Local PC Server (GPT4All)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = CommvaultNavy)
                            Text("Uses GPT4All instance running on your laptop.", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                if (localMode) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("PC IP Address") },
                        placeholder = { Text("e.g. 192.168.1.100") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = portText,
                        onValueChange = { portText = it },
                        label = { Text("Port (Default: 4891)") },
                        placeholder = { Text("4891") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text("System Prompt", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CommvaultNavy)
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("Instructions for AI") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    maxLines = 5
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = portText.toIntOrNull() ?: 4891
                    onSave(localMode, host, p, systemPrompt)
                },
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = LightSurface
    )
}
