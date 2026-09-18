package com.commvault.commlink.ui.shortcuts

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.shortcuts.model.*
import com.commvault.commlink.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(
    commLinkViewModel: CommLinkViewModel,
    shortcutsViewModel: ShortcutsViewModel,
    onBack: () -> Unit,
    onNavigateToBuilder: (String?) -> Unit
) {
    val context = LocalContext.current
    val connectionState by commLinkViewModel.connectionState.collectAsState()
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val isMacroRunning by commLinkViewModel.isMacroRunning.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("My Shortcuts", "Macros", "Payloads")

    LaunchedEffect(Unit) { shortcutsViewModel.loadShortcuts(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shortcuts", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBg)
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = {
                        shortcutsViewModel.initBuilder(null)
                        onNavigateToBuilder(null)
                    },
                    containerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Shortcut")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = LightBg,
                contentColor = CommvaultNavy,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = com.commvault.commlink.ui.theme.LocalPrimaryColor.current
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        selectedContentColor = CommvaultNavy,
                        unselectedContentColor = NeutralSlate,
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> MyShortcutsTab(shortcutsViewModel, commLinkViewModel, isConnected, isMacroRunning, context, onNavigateToBuilder)
                1 -> MacrosLibraryTab(commLinkViewModel, isConnected, isMacroRunning)
                2 -> PayloadInjectorTab(shortcutsViewModel, commLinkViewModel, isConnected, isMacroRunning)
            }
        }
    }
}

// ═══════════════════════════════════════════
// TAB 1: My Shortcuts
// ═══════════════════════════════════════════
@Composable
private fun MyShortcutsTab(
    vm: ShortcutsViewModel,
    clvm: CommLinkViewModel,
    isConnected: Boolean,
    isMacroRunning: Boolean,
    context: Context,
    onNavigateToBuilder: (String?) -> Unit
) {
    val shortcuts by vm.shortcuts.collectAsState()
    val shortcutColors = listOf(com.commvault.commlink.ui.theme.LocalPrimaryColor.current, Color(0xFF5C6BC0), Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFF26C6DA), Color(0xFF7E57C2))

    if (shortcuts.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = TextTertiary.copy(alpha = 0.3f), modifier = Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("No shortcuts yet", color = TextTertiary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Tap + to build your first automation", color = TextTertiary.copy(alpha = 0.6f), fontSize = 12.sp)
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shortcuts) { shortcut ->
                val color = shortcutColors.getOrElse(shortcut.colorIndex) { com.commvault.commlink.ui.theme.LocalPrimaryColor.current }
                var showOptions by remember { mutableStateOf(false) }
                val haptic = LocalHapticFeedback.current

                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, BorderLight),
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .pointerInput(isConnected, isMacroRunning, showOptions) {
                                detectTapGestures(
                                    onTap = { 
                                        if (showOptions) {
                                            showOptions = false
                                        } else if (isConnected && !isMacroRunning) {
                                            vm.executeShortcut(shortcut, clvm)
                                        }
                                    },
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        showOptions = true
                                    }
                                )
                            }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).background(color.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            
                            if (showOptions) {
                                IconButton(onClick = {
                                    vm.initBuilder(shortcut)
                                    onNavigateToBuilder(shortcut.id)
                                    showOptions = false
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextTertiary, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { 
                                    vm.deleteShortcut(context, shortcut.id) 
                                    showOptions = false
                                }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ErrorRed.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(shortcut.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${shortcut.tiles.size} actions", fontSize = 11.sp, color = TextTertiary)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════
// TAB 2: Macros Library
// ═══════════════════════════════════════════
@Composable
private fun MacrosLibraryTab(clvm: CommLinkViewModel, isConnected: Boolean, isMacroRunning: Boolean) {
    val categories = TileCategory.values().filter { cat -> ActionType.values().any { it.category == cat } }
    var expandedCategory by remember { mutableStateOf<TileCategory?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val actions = ActionType.values().filter { it.category == category }
            val isExpanded = expandedCategory == category

            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(1.dp, BorderLight)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedCategory = if (isExpanded) null else category }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(category.label, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${actions.size}", fontSize = 12.sp, color = TextTertiary, modifier = Modifier.padding(end = 8.dp))
                        Icon(
                            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null, tint = TextTertiary
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp)) {
                            actions.forEach { action ->
                                MacroActionRow(action, isConnected, isMacroRunning) {
                                    val vm = ShortcutsViewModel()
                                    val tile = ActionTile(type = action)
                                    vm.executeTiles(listOf(tile), clvm)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun MacroActionRow(action: ActionType, isConnected: Boolean, isMacroRunning: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = LightSurfaceAlt,
        onClick = onClick,
        enabled = isConnected && !isMacroRunning
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(32.dp).background(action.color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(action.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (isConnected) TextPrimary else TextTertiary)
                Text(action.description, fontSize = 10.sp, color = TextTertiary)
            }
            Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = if (isConnected) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else TextTertiary.copy(alpha = 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

// ═══════════════════════════════════════════
// TAB 3: Payload Injector
// ═══════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayloadInjectorTab(vm: ShortcutsViewModel, clvm: CommLinkViewModel, isConnected: Boolean, isMacroRunning: Boolean) {
    val payloads = vm.getPayloadTemplates()
    val categories = PayloadCategory.values()
    var selectedCategory by remember { mutableStateOf(PayloadCategory.RECON) }
    var customScript by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf<PayloadTemplate?>(null) }

    if (showConfirmDialog != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = null },
            title = { Text("Execute Payload?", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(showConfirmDialog!!.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(showConfirmDialog!!.description, fontSize = 12.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = LightSurfaceAlt) {
                        Text(
                            showConfirmDialog!!.script,
                            modifier = Modifier.padding(8.dp),
                            fontSize = 10.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.executePayload(showConfirmDialog!!.script, clvm)
                        showConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Execute") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Category chips
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.label, fontSize = 11.sp) },
                        leadingIcon = { Icon(cat.icon, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = cat.color.copy(alpha = 0.15f),
                            selectedLabelColor = cat.color
                        )
                    )
                }
            }
        }

        // Filtered payloads
        val filtered = if (selectedCategory == PayloadCategory.CUSTOM) emptyList() else payloads.filter { it.category == selectedCategory }
        items(filtered) { payload ->
            Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(1.dp, BorderLight)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isConnected && !isMacroRunning) { showConfirmDialog = payload }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).background(payload.category.color.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(payload.category.icon, contentDescription = null, tint = payload.category.color, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(payload.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
                        Text(payload.description, fontSize = 10.sp, color = TextTertiary)
                    }
                    Icon(Icons.Default.PlayArrow, contentDescription = "Execute", tint = if (isConnected) payload.category.color else TextTertiary.copy(alpha = 0.3f), modifier = Modifier.size(22.dp))
                }
            }
        }

        // Custom Payload Editor (always visible on Custom tab)
        if (selectedCategory == PayloadCategory.CUSTOM) {
            item {
                Surface(shape = RoundedCornerShape(14.dp), color = CardSurface, border = BorderStroke(1.dp, BorderLight)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Custom DuckyScript Payload", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customScript,
                            onValueChange = { customScript = it },
                            modifier = Modifier.fillMaxWidth().height(200.dp),
                            placeholder = { Text("REM My custom payload\nGUI r\nDELAY 500\nSTRING cmd\nENTER", fontSize = 12.sp, color = TextTertiary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current,
                                unfocusedBorderColor = BorderColor,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { vm.executePayload(customScript, clvm) },
                            enabled = isConnected && !isMacroRunning && customScript.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                        ) {
                            Icon(Icons.Default.FlashOn, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inject Payload", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}
