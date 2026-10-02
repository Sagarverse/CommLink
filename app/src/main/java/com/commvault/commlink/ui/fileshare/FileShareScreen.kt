package com.commvault.commlink.ui.fileshare

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.data.network.CommLinkFileServer
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.log10
import kotlin.math.pow

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileShareScreen(viewModel: CommLinkViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var isServerRunning by remember { mutableStateOf(false) }
    var serverUrl by remember { mutableStateOf("") }
    val clipboardManager = LocalClipboardManager.current
    val primaryColor = LocalPrimaryColor.current
    
    // Server instance
    val server = remember { CommLinkFileServer(context) }
    val baseDir = remember { java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CommLink") }
    
    var filesList by remember { mutableStateOf(emptyList<File>()) }
    
    fun refreshFiles() {
        if (!baseDir.exists()) baseDir.mkdirs()
        if (baseDir.exists()) {
            filesList = baseDir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
        }
    }

    LaunchedEffect(Unit) {
        refreshFiles()
        // Simple auto-refresh loop to catch new files pushed from the PC
        while (true) {
            delay(3000)
            refreshFiles()
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        var count = 0
        uris.forEach { uri ->
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    var fileName = "shared_file_${System.currentTimeMillis()}"
                    if (cursor != null && cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            fileName = cursor.getString(nameIndex)
                        }
                        cursor.close()
                    }
                    
                    val targetFile = File(baseDir, fileName)
                    targetFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    count++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (count > 0) {
            android.widget.Toast.makeText(context, "Added $count file(s) to share", android.widget.Toast.LENGTH_SHORT).show()
            refreshFiles()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (server.isAlive) {
                server.stop()
            }
        }
    }

    fun getLocalIpAddress(): String {
        try {
            val en = java.net.NetworkInterface.getNetworkInterfaces()
            while (en.hasMoreElements()) {
                val intf = en.nextElement()
                if (intf.isLoopback || !intf.isUp) continue
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is java.net.Inet4Address) {
                        return inetAddress.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        } catch (ex: Exception) { }
        return "0.0.0.0"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Share", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryColor)
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        try {
                            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clipData = clipManager.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                if (text.isNotEmpty()) {
                                    viewModel.sendText(text)
                                    android.widget.Toast.makeText(context, "Pushed clipboard to PC!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Clipboard is empty!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Error reading clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync Text", tint = primaryColor)
                    }
                    IconButton(onClick = { filePickerLauncher.launch("*/*") }) {
                        Icon(Icons.Default.Add, contentDescription = "Share File", tint = primaryColor)
                    }
                    IconButton(onClick = { 
                        try {
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", baseDir)
                            intent.setDataAndType(uri, "resource/folder")
                            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            if (intent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(intent)
                            } else {
                                android.widget.Toast.makeText(context, "No file manager found. Files saved in Downloads/CommLink", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch(e: Exception) {
                            android.widget.Toast.makeText(context, "Files saved in Downloads/CommLink", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) {
                        Icon(Icons.Default.Folder, contentDescription = "Open Folder", tint = primaryColor)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PageBackground)
            )
        },
        containerColor = PageBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Server Status Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color(0xFF334155).copy(alpha = 0.1f))
                    .clip(RoundedCornerShape(24.dp))
                    .background(CardSurface)
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = if (isServerRunning) Icons.Default.WifiTethering else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = if (isServerRunning) primaryColor else TextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isServerRunning) {
                        Text("Server Active", color = primaryColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(primaryColor.copy(alpha = 0.1f))
                                .clickable {
                                    clipboardManager.setText(AnnotatedString(serverUrl))
                                    android.widget.Toast.makeText(context, "URL copied!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(serverUrl, color = primaryColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Type this in your PC browser to send/receive files.", color = TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    } else {
                        Text("Server Offline", color = TextTertiary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Start the server to browse and transfer files over WiFi.", color = TextTertiary, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = {
                            if (isServerRunning) {
                                server.stop()
                                isServerRunning = false
                            } else {
                                try {
                                    server.start()
                                    val ip = getLocalIpAddress()
                                    serverUrl = "http://$ip:8080"
                                    isServerRunning = true
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Failed to start server", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServerRunning) CardSurfaceAlt else primaryColor,
                            contentColor = if (isServerRunning) TextPrimary else Color.White
                        )
                    ) {
                        Text(if (isServerRunning) "Stop Server" else "Start Server", fontWeight = FontWeight.Bold)
                    }
                }
            }
            
            // File List Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Shared Files", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("${filesList.size} Items", color = TextTertiary, fontSize = 12.sp)
            }
            
            // File List
            if (filesList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextTertiary.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No files shared", color = TextTertiary, fontSize = 16.sp)
                        Text("Tap + to add files or send them from your PC", color = TextTertiary, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filesList) { file ->
                        FileItemCard(
                            file = file,
                            primaryColor = primaryColor,
                            onClick = {
                                try {
                                    val uri = androidx.core.content.FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "*/*"
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, mimeType)
                                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Could not open file", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDelete = {
                                file.delete()
                                refreshFiles()
                                android.widget.Toast.makeText(context, "File deleted", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileItemCard(file: File, primaryColor: Color, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(
            modifier = Modifier.size(44.dp).background(primaryColor.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val isImage = file.name.endsWith(".jpg", true) || file.name.endsWith(".png", true)
            val isVideo = file.name.endsWith(".mp4", true) || file.name.endsWith(".mkv", true)
            
            Icon(
                imageVector = if (isImage) Icons.Default.Image else if (isVideo) Icons.Default.Videocam else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = primaryColor
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatBytes(file.length()),
                color = TextTertiary,
                fontSize = 12.sp
            )
        }
        
        // Share button
        val context = LocalContext.current
        IconButton(onClick = {
            try {
                val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share File"))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Could not share file", android.widget.Toast.LENGTH_SHORT).show()
            }
        }) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = primaryColor)
        }
        
        // Delete button
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
        }
    }
}
