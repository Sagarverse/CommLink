package com.commvault.commlink.ui.alarm

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commvault.commlink.domain.model.AlarmItem
import com.commvault.commlink.domain.model.HydrationReminder
import com.commvault.commlink.ui.CommLinkViewModel
import com.commvault.commlink.ui.theme.*
import com.commvault.commlink.ui.components.CeramicCard
import java.util.Calendar
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: CommLinkViewModel,
    onBack: () -> Unit
) {
    val alarms by viewModel.alarmsList.collectAsState()
    val hydrationReminders by viewModel.hydrationReminders.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<AlarmItem?>(null) }
    val context = LocalContext.current

    val handleBack = {
        if (showForm) {
            showForm = false
        } else {
            onBack()
        }
    }

    androidx.activity.compose.BackHandler(onBack = handleBack)

    Scaffold(
        topBar = {
            Surface(shadowElevation = 4.dp) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = LocalPrimaryColor.current, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Advanced Alarms", color = CommvaultNavy, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = LightSurface)
                )
            }
        },
        floatingActionButton = {
            if (!showForm) {
                FloatingActionButton(
                    onClick = {
                        alarmToEdit = null
                        showForm = true
                    },
                    containerColor = LocalPrimaryColor.current
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Alarm", tint = Color.White)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(colors = listOf(LightBg, LocalPrimarySoft.current.copy(alpha = 0.2f))))
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = showForm,
                label = "AlarmViewToggle"
            ) { isFormVisible ->
                if (isFormVisible) {
                    AlarmForm(
                        initialAlarm = alarmToEdit,
                        onSave = { newAlarm ->
                            viewModel.saveAlarm(newAlarm, context)
                            showForm = false
                        },
                        onCancel = { showForm = false }
                    )
                } else {
                    AlarmList(
                        alarms = alarms,
                        hydrationReminders = hydrationReminders,
                        onToggle = { id, isEnabled -> viewModel.toggleAlarm(id, isEnabled, context) },
                        onDelete = { id -> viewModel.deleteAlarm(id, context) },
                        onEdit = { alarm ->
                            alarmToEdit = alarm
                            showForm = true
                        },
                        onStopActive = { viewModel.stopAlarms() },
                        onAddHydrationReminder = { hour, minute ->
                            val reminder = HydrationReminder(
                                id = UUID.randomUUID().toString(),
                                hour = hour,
                                minute = minute,
                                isEnabled = true,
                                label = "Hydration Reminder"
                            )
                            viewModel.saveHydrationReminder(reminder, context)
                        },
                        onToggleHydrationReminder = { id, enabled ->
                            viewModel.toggleHydrationReminder(id, enabled, context)
                        },
                        onDeleteHydrationReminder = { id ->
                            viewModel.deleteHydrationReminder(id, context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlarmList(
    alarms: List<AlarmItem>,
    hydrationReminders: List<HydrationReminder>,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (AlarmItem) -> Unit,
    onStopActive: () -> Unit,
    onAddHydrationReminder: (Int, Int) -> Unit,
    onToggleHydrationReminder: (String, Boolean) -> Unit,
    onDeleteHydrationReminder: (String) -> Unit
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // --- Hydration Reminders Section ---
            item {
                HydrationSectionHeader(
                    onAddClick = { showTimePicker = true }
                )
            }

            if (hydrationReminders.isEmpty()) {
                item {
                    CeramicCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hydration reminders yet.\nTap + to add one!",
                                color = TextTertiary,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(hydrationReminders) { reminder ->
                    HydrationReminderCard(
                        reminder = reminder,
                        onToggle = { enabled -> onToggleHydrationReminder(reminder.id, enabled) },
                        onDelete = { onDeleteHydrationReminder(reminder.id) }
                    )
                }
            }

            // --- Spacer between sections ---
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // --- Alarms Section Header ---
            item {
                Text(
                    text = "SCHEDULED ALARMS",
                    color = TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            if (alarms.isEmpty()) {
                item {
                    CeramicCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No Alarms Scheduled", color = TextTertiary, fontSize = 14.sp)
                        }
                    }
                }
            } else {
                items(alarms) { alarm ->
                    AlarmCardItem(alarm, onToggle, onDelete, { onEdit(alarm) })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onStopActive,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
        ) {
            Text("Stop Ringing Alarms", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }

    // Time picker dialog for hydration reminder
    if (showTimePicker) {
        val cal = Calendar.getInstance()
        android.app.TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onAddHydrationReminder(hourOfDay, minute)
                showTimePicker = false
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true
        ).apply {
            setOnCancelListener { showTimePicker = false }
            show()
        }
    }
}

@Composable
fun HydrationSectionHeader(onAddClick: () -> Unit) {
    val waterBlue = Color(0xFF2196F3)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.WaterDrop,
                contentDescription = null,
                tint = waterBlue,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "HYDRATION REMINDERS",
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        IconButton(onClick = onAddClick, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Hydration Reminder",
                tint = waterBlue,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun HydrationReminderCard(
    reminder: HydrationReminder,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val waterBlue = Color(0xFF2196F3)
    val timeStr = String.format("%02d:%02d", reminder.hour, reminder.minute)

    CeramicCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.WaterDrop,
                contentDescription = null,
                tint = if (reminder.isEnabled) waterBlue else TextTertiary.copy(alpha = 0.4f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = timeStr,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (reminder.isEnabled) CommvaultNavy else TextTertiary
                )
                Text(
                    text = "Daily • ${reminder.label}",
                    fontSize = 12.sp,
                    color = if (reminder.isEnabled) waterBlue else TextTertiary.copy(alpha = 0.5f)
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
            }

            Switch(
                checked = reminder.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = waterBlue
                )
            )
        }
    }
}

@Composable
fun AlarmCardItem(
    alarm: AlarmItem,
    onToggle: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClick: () -> Unit
) {
    val cal = Calendar.getInstance().apply { timeInMillis = alarm.timeInMillis }
    val isPast = alarm.timeInMillis < System.currentTimeMillis()
    
    CeramicCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val timeStr = String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
                val dateStr = "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
                
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(timeStr, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = if (alarm.isEnabled) CommvaultNavy else TextTertiary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateStr, fontSize = 14.sp, color = TextTertiary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text(alarm.title, fontSize = 14.sp, color = if (alarm.isEnabled) TextPrimary else TextTertiary)
                Text(alarm.alarmType, fontSize = 12.sp, color = LocalPrimaryColor.current)
                if (isPast && alarm.isEnabled) {
                    Text("(Expired)", fontSize = 12.sp, color = Color.Red)
                }
            }

            IconButton(onClick = { onDelete(alarm.id) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.6f))
            }
            
            Switch(
                checked = alarm.isEnabled,
                onCheckedChange = { onToggle(alarm.id, it) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = LocalPrimaryColor.current)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmForm(
    initialAlarm: AlarmItem?,
    onSave: (AlarmItem) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val initialCal = Calendar.getInstance().apply {
        if (initialAlarm != null) timeInMillis = initialAlarm.timeInMillis
    }
    
    var title by remember { mutableStateOf(initialAlarm?.title ?: "") }
    var selectedDate by remember { mutableStateOf(initialCal) }
    var selectedTime by remember { mutableStateOf(initialCal) }
    
    val ringTypes = listOf("Standard", "Escalating", "Interval")
    var selectedRingType by remember { mutableStateOf(ringTypes.indexOf(initialAlarm?.alarmType ?: "Standard").coerceAtLeast(0)) }
    
    var maxVolumeStr by remember { mutableStateOf((initialAlarm?.maxVolume ?: 10).toString()) }
    var intervalSecsStr by remember { mutableStateOf((initialAlarm?.intervalSecs ?: 5).toString()) }
    var repeatCountStr by remember { mutableStateOf((initialAlarm?.repeatCount ?: 10).toString()) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = LightSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.padding(16.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (initialAlarm == null) "NEW ALARM" else "EDIT ALARM", color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                TextButton(onClick = onCancel) { Text("Cancel", color = TextTertiary) }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Alarm Title") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LocalPrimaryColor.current, unfocusedBorderColor = BorderColor)
            )

            // Pickers
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val newCal = Calendar.getInstance().apply { timeInMillis = selectedDate.timeInMillis }
                                newCal.set(Calendar.YEAR, year)
                                newCal.set(Calendar.MONTH, month)
                                newCal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                selectedDate = newCal
                            },
                            selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LightSurfaceAlt, contentColor = CommvaultNavy),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("${selectedDate.get(Calendar.DAY_OF_MONTH)}/${selectedDate.get(Calendar.MONTH) + 1}/${selectedDate.get(Calendar.YEAR)}", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        android.app.TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val newCal = Calendar.getInstance().apply { timeInMillis = selectedTime.timeInMillis }
                                newCal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                newCal.set(Calendar.MINUTE, minute)
                                newCal.set(Calendar.SECOND, 0)
                                selectedTime = newCal
                            },
                            selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE), true
                        ).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = LightSurfaceAlt, contentColor = CommvaultNavy),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(String.format("%02d:%02d", selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE)), fontWeight = FontWeight.Bold)
                }
            }

            Text("RING TYPE", color = TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Surface(shape = RoundedCornerShape(12.dp), color = LightSurfaceAlt, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(4.dp)) {
                    ringTypes.forEachIndexed { index, rTitle ->
                        val isSelected = selectedRingType == index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) LightSurface else Color.Transparent,
                            shadowElevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier.weight(1f).padding(2.dp),
                            onClick = { selectedRingType = index }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(rTitle, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, color = if (isSelected) CommvaultNavy else TextTertiary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = selectedRingType == 1) {
                OutlinedTextField(
                    value = maxVolumeStr, onValueChange = { maxVolumeStr = it },
                    label = { Text("Max Volume (1-10)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LocalPrimaryColor.current, unfocusedBorderColor = BorderColor)
                )
            }
            AnimatedVisibility(visible = selectedRingType == 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = intervalSecsStr, onValueChange = { intervalSecsStr = it }, label = { Text("Interval (sec)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LocalPrimaryColor.current, unfocusedBorderColor = BorderColor)
                    )
                    OutlinedTextField(
                        value = repeatCountStr, onValueChange = { repeatCountStr = it }, label = { Text("Repeat Count") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LocalPrimaryColor.current, unfocusedBorderColor = BorderColor)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (title.isNotEmpty()) {
                        val targetCal = Calendar.getInstance().apply {
                            set(Calendar.YEAR, selectedDate.get(Calendar.YEAR))
                            set(Calendar.MONTH, selectedDate.get(Calendar.MONTH))
                            set(Calendar.DAY_OF_MONTH, selectedDate.get(Calendar.DAY_OF_MONTH))
                            set(Calendar.HOUR_OF_DAY, selectedTime.get(Calendar.HOUR_OF_DAY))
                            set(Calendar.MINUTE, selectedTime.get(Calendar.MINUTE))
                            set(Calendar.SECOND, 0)
                        }
                        val alarmTypeStr = ringTypes[selectedRingType]
                        val maxVol = maxVolumeStr.toIntOrNull() ?: 10
                        val intervalSecs = intervalSecsStr.toIntOrNull() ?: 5
                        val repeatCount = repeatCountStr.toIntOrNull() ?: 10
                        
                        val newAlarm = AlarmItem(
                            id = initialAlarm?.id ?: UUID.randomUUID().toString(),
                            title = title,
                            timeInMillis = targetCal.timeInMillis,
                            alarmType = alarmTypeStr,
                            maxVolume = maxVol,
                            intervalSecs = intervalSecs,
                            repeatCount = repeatCount,
                            isEnabled = true
                        )
                        onSave(newAlarm)
                    } else {
                        android.widget.Toast.makeText(context, "Title required", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = LocalPrimaryColor.current)
            ) {
                Text("Save Alarm", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}
