package com.commvault.commlink.ui.assigned

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.data.model.AssignedWork
import com.commvault.commlink.data.model.WorkPriority
import com.commvault.commlink.data.model.WorkStatus
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignedWorksScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val works by viewModel.assignedWorks.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        CreateAssignmentDialog(
            peers = discoveredPeers.map { it.deviceName },
            onDismiss = { showDialog = false },
            onSubmit = { title, desc, prio, date, assignee ->
                val work = com.commvault.commlink.data.model.AssignedWork(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    description = desc,
                    managerName = "Me",
                    dueDate = date,
                    priority = prio
                )
                viewModel.sendAssignedWork(assignee, work, context) {
                    showDialog = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Assigned Works", color = CommvaultNavy, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = LightBg
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = CommvaultPink,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Assign Work")
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LightBg)
                .padding(paddingValues)
        ) {
            if (works.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(64.dp), tint = TextTertiary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No works assigned to you right now.", color = TextSecondary)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(works) { work ->
                        AssignedWorkCard(
                            work = work,
                            onStatusChange = { newStatus ->
                                viewModel.updateWorkStatus(work.id, newStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AssignedWorkCard(
    work: AssignedWork,
    onStatusChange: (WorkStatus) -> Unit
) {
    val priorityColor = when (work.priority) {
        WorkPriority.CRITICAL -> ErrorRed
        WorkPriority.HIGH -> CommvaultPink
        WorkPriority.MEDIUM -> CommvaultNavy
        WorkPriority.LOW -> TextTertiary
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = LightSurface,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = work.title,
                    color = CommvaultNavy,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    color = priorityColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = work.priority.name,
                        color = priorityColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = work.description,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            HorizontalDivider(color = BorderColor)

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Assigned by: ${work.managerName}",
                    color = TextPrimary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Due: ${work.dueDate}",
                    color = ErrorRed.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Status actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (work.status == WorkStatus.COMPLETED) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessTeal)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Completed", color = SuccessTeal, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val newStatus = if (work.status == WorkStatus.PENDING) WorkStatus.IN_PROGRESS else WorkStatus.COMPLETED
                            onStatusChange(newStatus)
                        }
                    ) {
                        Text(
                            text = if (work.status == WorkStatus.PENDING) "Mark In Progress" else "Mark Completed",
                            color = CommvaultNavy
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAssignmentDialog(
    peers: List<String>,
    onDismiss: () -> Unit,
    onSubmit: (title: String, desc: String, priority: WorkPriority, date: String, assignee: String) -> Unit
) {
    val displayPeers = if (peers.isEmpty()) com.commvault.commlink.ui.help.mockTeam.map { it.name } else peers
    var title by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var desc by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var date by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var assignee by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(displayPeers.firstOrNull() ?: "") }
    var priority by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(WorkPriority.MEDIUM) }

    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var prioExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text("Assign Work", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, label = { Text("Task Title") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it }, label = { Text("Due Date (e.g. Tomorrow 5 PM)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = assignee, onValueChange = {}, readOnly = true, label = { Text("Assign To") }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded, 
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.heightIn(max = 200.dp)
                    ) {
                        displayPeers.forEach { p ->
                            DropdownMenuItem(text = { Text(p) }, onClick = { assignee = p; expanded = false })
                        }
                    }
                }

                ExposedDropdownMenuBox(expanded = prioExpanded, onExpandedChange = { prioExpanded = it }) {
                    OutlinedTextField(
                        value = priority.name, onValueChange = {}, readOnly = true, label = { Text("Priority") }, modifier = Modifier.fillMaxWidth().menuAnchor(), shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = prioExpanded, 
                        onDismissRequest = { prioExpanded = false },
                        modifier = Modifier.heightIn(max = 150.dp)
                    ) {
                        WorkPriority.values().forEach { p ->
                            DropdownMenuItem(text = { Text(p.name) }, onClick = { priority = p; prioExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank() && assignee.isNotBlank()) onSubmit(title, desc, priority, date, assignee) },
                colors = ButtonDefaults.buttonColors(containerColor = CommvaultPink),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Assign", color = Color.White) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}
