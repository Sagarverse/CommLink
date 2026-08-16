package com.commvault.commlink.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: CommLinkViewModel,
    onNavigateToRoom: (String) -> Unit,
    onBack: () -> Unit
) {
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()

    val recentChats = chatMessages.flatMap { listOf(it.senderName, it.receiverName) }
        .distinct()
        .filter { it != viewModel.email.value.substringBefore("@").uppercase() }
    
    val allPeers = (discoveredPeers.map { it.deviceName } + recentChats).distinct().sorted()

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Wifi, contentDescription = null, tint = CommvaultPink, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Local Chat", color = CommvaultNavy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("${discoveredPeers.size} peers on Wi-Fi", color = TextSecondary, fontSize = 11.sp)
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
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues)
        ) {
            if (allPeers.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Wifi, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No team members found on local Wi-Fi.", color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text("Make sure others have CommLink running.", color = TextTertiary, fontSize = 12.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(allPeers) { index, peerName ->
                        val isOnline = discoveredPeers.any { it.deviceName == peerName }
                        val lastMessage = chatMessages.filter { it.senderName == peerName || it.receiverName == peerName }
                            .maxByOrNull { it.timestamp }

                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay((index * 60).toLong())
                            visible = true
                        }

                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { 30 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            PeerChatCard(
                                name = peerName,
                                isOnline = isOnline,
                                lastMessageText = lastMessage?.text ?: "Tap to chat",
                                onClick = { onNavigateToRoom(peerName) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeerChatCard(
    name: String,
    isOnline: Boolean,
    lastMessageText: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface,
        shadowElevation = 3.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isOnline) SuccessTeal.copy(alpha = 0.3f) else BorderColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Premium avatar with gradient ring for online
            Box(contentAlignment = Alignment.Center) {
                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                Brush.linearGradient(GradientTealMint),
                                CircleShape
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .size(if (isOnline) 50.dp else 50.dp)
                        .background(LightSurfaceAlt, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = name.take(1),
                        color = CommvaultNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        color = CommvaultNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    if (isOnline) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessTealLight
                        ) {
                            Text(
                                "ONLINE",
                                color = SuccessTeal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = lastMessageText,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
