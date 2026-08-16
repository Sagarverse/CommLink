package com.commvault.commlink.ui.help

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import com.commvault.commlink.ui.theme.*
import kotlinx.coroutines.delay

data class TeamMember(val name: String, val role: String, val phone: String)

val mockTeam = listOf(
    TeamMember("HIMATHI", "Customer Success Apprentice", "+916302240664"),
    TeamMember("KARAN", "Customer Success Apprentice", "+919691100746"),
    TeamMember("MANOJ", "Customer Success Apprentice", "+916364205955"),
    TeamMember("MONIKA", "Customer Success Apprentice", "+919980297370"),
    TeamMember("NEHA", "Customer Success Apprentice", "+919846550843"),
    TeamMember("SAGAR", "Customer Success Apprentice", "+919019989269"),
    TeamMember("YASH", "Customer Success Apprentice", "+917899538612"),
    TeamMember("SUYOG", "Manager", "+918745018688")
)

// Color for each member avatar
private val avatarGradients = listOf(
    listOf(Color(0xFFFF6B6B), Color(0xFFFF8E8E)),
    listOf(Color(0xFF4ECDC4), Color(0xFF6BE5DB)),
    listOf(Color(0xFF45B7D1), Color(0xFF6DC8DE)),
    listOf(Color(0xFFF9CA24), Color(0xFFFBD848)),
    listOf(Color(0xFFFF9FF3), Color(0xFFFFB8F8)),
    listOf(Color(0xFF00C49A), Color(0xFF00E5B0)),
    listOf(Color(0xFF6C5CE7), Color(0xFF8B7CEB)),
    listOf(Color(0xFFFF4A6A), Color(0xFFFF7B90))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    viewModel: com.commvault.commlink.ui.CommLinkViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedActionMember by remember { mutableStateOf<TeamMember?>(null) }
    
    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            selectedActionMember?.let { member ->
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:${member.phone}"))
                context.startActivity(intent)
            }
            selectedActionMember = null
        } else {
            Toast.makeText(context, "Call permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    if (selectedActionMember != null) {
        val member = selectedActionMember!!
        AlertDialog(
            onDismissRequest = { selectedActionMember = null },
            title = { Text("Action for ${member.name}", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
            text = { Text("Choose an action:", color = TextSecondary) },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Brush.horizontalGradient(GradientTealMint), RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("📞 Call", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            viewModel.sendEmergencyPing(member.name, context)
                            selectedActionMember = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        contentPadding = PaddingValues(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(Brush.horizontalGradient(GradientSunrise), RoundedCornerShape(10.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text("🚨 Emergency", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedActionMember = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = LightSurface,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Groups, contentDescription = null, tint = CommvaultPink, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Team Directory", color = CommvaultNavy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                Text("${mockTeam.size} members", color = TextSecondary, fontSize = 11.sp)
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
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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
                    .padding(16.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CommvaultPinkSoft.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Double-tap → Ping  •  Long-press → Call / Emergency",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(mockTeam) { index, member ->
                        var visible by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            delay((index * 80).toLong())
                            visible = true
                        }

                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { 40 },
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                            )
                        ) {
                            MemberCard(
                                member = member,
                                gradientColors = avatarGradients[index % avatarGradients.size],
                                onDoubleTap = {
                                    viewModel.sendHelpPing(member.name, context)
                                },
                                onLongPress = {
                                    selectedActionMember = member
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
fun MemberCard(
    member: TeamMember,
    gradientColors: List<Color>,
    onDoubleTap: () -> Unit,
    onLongPress: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { onDoubleTap() },
                    onLongPress = { onLongPress() }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = LightSurface,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Gradient avatar
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(
                        Brush.linearGradient(gradientColors),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.name.take(1),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = member.name,
                color = CommvaultNavy,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(2.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (member.role == "Manager") CommvaultPinkSoft else LightSurfaceAlt
            ) {
                Text(
                    text = if (member.role == "Manager") "★ MANAGER" else member.role.uppercase().take(12),
                    color = if (member.role == "Manager") CommvaultPink else TextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}
