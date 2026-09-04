package com.commvault.commlink.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.commvault.commlink.data.model.ChatMessage
import com.commvault.commlink.data.model.MessageStatus
import com.commvault.commlink.data.model.MessageType
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    viewModel: CommLinkViewModel,
    peerName: String,
    onBack: () -> Unit
) {
    val chatMessages by viewModel.chatMessages.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    
    val roomMessages = chatMessages.filter { it.senderName == peerName || it.receiverName == peerName }
        .sortedBy { it.timestamp }
        
    val isOnline = discoveredPeers.any { it.deviceName == peerName }

    var textInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom on new message
    LaunchedEffect(roomMessages.size) {
        if (roomMessages.isNotEmpty()) {
            listState.animateScrollToItem(roomMessages.size - 1)
        }
    }

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var name = "file"
            var size = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    name = it.getString(it.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME))
                    size = it.getLong(it.getColumnIndexOrThrow(android.provider.OpenableColumns.SIZE))
                }
            }
            viewModel.sendFileMessage(peerName, uri, name, size, context)
        }
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Avatar
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (isOnline) Brush.linearGradient(GradientTealMint) 
                                        else Brush.linearGradient(listOf(LightSurfaceAlt, LightSurfaceAlt)),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier.size(34.dp).background(LightSurface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(peerName.take(1), fontWeight = FontWeight.Bold, color = CommvaultNavy, fontSize = 16.sp)
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(peerName, color = CommvaultNavy, fontWeight = FontWeight.Bold)
                                Text(
                                    if (isOnline) "Online on local Wi-Fi" else "Offline", 
                                    color = if (isOnline) SuccessTeal else TextTertiary, 
                                    fontSize = 11.sp
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
                )
            }
        },
        bottomBar = {
            Surface(
                color = LightSurface,
                shadowElevation = 12.dp
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Attach button
                    IconButton(
                        onClick = { launcher.launch("*/*") },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Attach File", tint = LocalPrimaryColor.current, modifier = Modifier.size(26.dp))
                    }
                    
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type message...", color = TextTertiary) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalPrimaryColor.current,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        maxLines = 3
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Gradient send button
                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendChatMessage(peerName, textInput)
                                textInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(listOf(LocalPrimaryColor.current, LocalPrimaryLight.current)),
                                CircleShape
                            )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(roomMessages) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
    }
    
    val isMe = msg.isFromMe
    val bubbleColor = if (isMe) LocalPrimaryColor.current else LightSurface
    val textColor = if (isMe) Color.White else TextPrimary

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { 30 },
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        ) + fadeIn()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = if (isMe) 18.dp else 4.dp,
                    bottomEnd = if (isMe) 4.dp else 18.dp
                ),
                color = bubbleColor,
                shadowElevation = 2.dp,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (msg.messageType == MessageType.FILE) {
                        // File message with premium card look
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isMe) Color.White.copy(alpha = 0.15f) else LightSurfaceAlt,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(
                                            if (isMe) Color.White.copy(alpha = 0.2f) else LocalPrimaryColor.current.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Description, contentDescription = "File", tint = if (isMe) Color.White else LocalPrimaryColor.current, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msg.fileName ?: "Unknown File",
                                        color = textColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    if (msg.fileSize != null && msg.fileSize > 0) {
                                        val sizeStr = when {
                                            msg.fileSize < 1024 -> "${msg.fileSize} B"
                                            msg.fileSize < 1024 * 1024 -> "${msg.fileSize / 1024} KB"
                                            else -> "${msg.fileSize / (1024 * 1024)} MB"
                                        }
                                        Text(text = sizeStr, color = textColor.copy(alpha = 0.6f), fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        if (msg.status == MessageStatus.DELIVERED && !isMe && msg.fileUri != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { /* TODO: Open file intent */ },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessTeal),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.fillMaxWidth().height(34.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Open File", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Text(text = msg.text, color = textColor, fontSize = 15.sp, lineHeight = 20.sp)
                    }
                    
                    // Status indicators
                    if (isMe) {
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (msg.status) {
                                    MessageStatus.SENDING -> "Sending..."
                                    MessageStatus.FAILED -> "Failed"
                                    else -> ""
                                },
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            when (msg.status) {
                                MessageStatus.FAILED -> Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(12.dp))
                                MessageStatus.SENT -> Icon(Icons.Default.Check, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                                MessageStatus.DELIVERED -> Icon(Icons.Default.DoneAll, contentDescription = null, tint = SuccessTeal, modifier = Modifier.size(14.dp))
                                else -> {}
                            }
                        }
                    }
                }
            }
        }
    }
}
