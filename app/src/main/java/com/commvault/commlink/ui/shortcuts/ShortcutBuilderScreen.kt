package com.commvault.commlink.ui.shortcuts

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.data.bluetooth.HidDeviceManager
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.shortcuts.model.*
import com.commvault.commlink.ui.theme.*
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutBuilderScreen(
    commLinkViewModel: CommLinkViewModel,
    shortcutsViewModel: ShortcutsViewModel,
    editId: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by commLinkViewModel.connectionState.collectAsState()
    val isConnected = connectionState is HidDeviceManager.ConnectionState.Connected
    val isMacroRunning by commLinkViewModel.isMacroRunning.collectAsState()
    
    val builderName by shortcutsViewModel.builderName.collectAsState()
    val builderColorIndex by shortcutsViewModel.builderColorIndex.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var editingTileIndex by remember { mutableIntStateOf(-1) }
    
    val shortcutColors = listOf(com.commvault.commlink.ui.theme.LocalPrimaryColor.current, Color(0xFF5C6BC0), Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFF26C6DA), Color(0xFF7E57C2))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "Edit Shortcut" else "New Shortcut", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Play button
                    IconButton(
                        onClick = { shortcutsViewModel.executeTiles(shortcutsViewModel.builderTiles.toList(), commLinkViewModel) },
                        enabled = isConnected && !isMacroRunning && shortcutsViewModel.builderTiles.isNotEmpty()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", tint = if (isConnected) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else TextTertiary)
                    }
                    // Save button
                    IconButton(onClick = {
                        shortcutsViewModel.saveCurrentShortcut(context, editId)
                        onBack()
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save", tint = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LightBg)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues)
        ) {
            // ─── Name & Color Picker ───
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = builderName,
                        onValueChange = { shortcutsViewModel.setBuilderName(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Shortcut Name") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current,
                            unfocusedBorderColor = BorderColor,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Color", fontSize = 11.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        shortcutColors.forEachIndexed { index, color ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(if (builderColorIndex == index) 2.dp else 0.dp, CommvaultNavy, CircleShape)
                                    .clickable { shortcutsViewModel.setBuilderColor(index) }
                            )
                        }
                    }
                }
            }

            // ─── Tiles List ───
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ACTIONS (${shortcutsViewModel.builderTiles.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextTertiary, letterSpacing = 1.5.sp)
                TextButton(onClick = { showAddSheet = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Action", fontSize = 12.sp)
                }
            }

            if (shortcutsViewModel.builderTiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Layers, contentDescription = null, tint = TextTertiary.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No actions added yet", color = TextTertiary, fontSize = 14.sp)
                        Text("Tap \"Add Action\" to start building", color = TextTertiary.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(shortcutsViewModel.builderTiles) { index, tile ->
                        TileRow(
                            index = index,
                            tile = tile,
                            isEditing = editingTileIndex == index,
                            onEdit = { editingTileIndex = if (editingTileIndex == index) -1 else index },
                            onDelete = { shortcutsViewModel.removeTile(index); editingTileIndex = -1 },
                            onParamChange = { shortcutsViewModel.updateTileParam(index, it) },
                            onMoveUp = { if (index > 0) shortcutsViewModel.moveTile(index, index - 1) },
                            onMoveDown = { if (index < shortcutsViewModel.builderTiles.size - 1) shortcutsViewModel.moveTile(index, index + 1) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }

            // ─── Execute Bar ───
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardSurface,
                shadowElevation = 8.dp
            ) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { shortcutsViewModel.executeTiles(shortcutsViewModel.builderTiles.toList(), commLinkViewModel) },
                        enabled = isConnected && !isMacroRunning && shortcutsViewModel.builderTiles.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Shortcut", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // ─── Add Action Bottom Sheet ───
    if (showAddSheet) {
        AddActionSheet(
            onDismiss = { showAddSheet = false },
            onSelect = { actionType ->
                shortcutsViewModel.addTile(actionType)
                showAddSheet = false
                if (actionType.paramHint != null) {
                    editingTileIndex = shortcutsViewModel.builderTiles.size - 1
                }
            }
        )
    }
}

@Composable
private fun TileRow(
    index: Int,
    tile: ActionTile,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onParamChange: (String) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, if (isEditing) com.commvault.commlink.ui.theme.LocalPrimaryColor.current else BorderLight)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Step number
                Box(
                    modifier = Modifier.size(24.dp).background(tile.type.color.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tile.type.color)
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Icon
                Icon(tile.type.icon, contentDescription = null, tint = tile.type.color, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                // Label
                Column(modifier = Modifier.weight(1f)) {
                    Text(tile.type.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (tile.param.isNotBlank()) {
                        Text(tile.param, fontSize = 10.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                // Controls
                IconButton(onClick = onMoveUp, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = TextTertiary, modifier = Modifier.size(16.dp))
                }
                if (tile.type.hasParam) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = com.commvault.commlink.ui.theme.LocalPrimaryColor.current, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(16.dp))
                }
            }
            // Inline param editor
            AnimatedVisibility(visible = isEditing && tile.type.hasParam) {
                if (tile.type == ActionType.MOUSE_CLICK) {
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val options = listOf("Left", "Right", "Middle")
                        options.forEach { opt ->
                            FilterChip(
                                selected = tile.param.equals(opt, ignoreCase = true),
                                onClick = { onParamChange(opt) },
                                label = { Text(opt, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current.copy(alpha = 0.15f),
                                    selectedLabelColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current
                                ),
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = tile.param,
                        onValueChange = onParamChange,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = { Text(tile.type.paramLabel) },
                        placeholder = { Text(tile.type.paramHint, fontSize = 11.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current, unfocusedBorderColor = BorderColor, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
                        shape = RoundedCornerShape(8.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddActionSheet(onDismiss: () -> Unit, onSelect: (ActionType) -> Unit) {
    val categories = TileCategory.values()
    var selectedCategory by remember { mutableStateOf(TileCategory.SYSTEM) }
    
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LightBg) {
        Column(modifier = Modifier.padding(horizontal = 16.dp).heightIn(max = 500.dp)) {
            Text("Add Action", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CommvaultNavy, modifier = Modifier.padding(bottom = 12.dp))
            
            var searchQuery by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                placeholder = { Text("Search actions...", fontSize = 13.sp) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface
                ),
                shape = RoundedCornerShape(10.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
            )
            
            // Category chips
            if (searchQuery.isBlank()) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current.copy(alpha = 0.15f),
                                selectedLabelColor = com.commvault.commlink.ui.theme.LocalPrimaryColor.current
                            ),
                            modifier = Modifier.height(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            
            // Actions list
            val actions = if (searchQuery.isNotBlank()) {
                ActionType.values().filter { it.label.contains(searchQuery, ignoreCase = true) || it.description.contains(searchQuery, ignoreCase = true) }
            } else {
                ActionType.values().filter { it.category == selectedCategory }
            }
            
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(actions.size) { index ->
                    val action = actions[index]
                    Surface(
                        onClick = { onSelect(action) },
                        shape = RoundedCornerShape(10.dp),
                        color = CardSurface,
                        border = BorderStroke(1.dp, BorderLight)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(30.dp).background(action.color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(action.icon, contentDescription = null, tint = action.color, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(action.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                                Text(action.description, fontSize = 10.sp, color = TextTertiary)
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }
}
