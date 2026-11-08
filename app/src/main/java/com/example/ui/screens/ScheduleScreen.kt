package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.models.DriverShift
import com.example.ui.theme.*
import com.example.viewmodel.RiderViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: RiderViewModel,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val shifts by viewModel.shifts.collectAsState()
    var selectedDayFilter by remember { mutableIntStateOf(0) } // 0 = All days, 1..7 = Mon..Sun
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredShifts = remember(shifts, selectedDayFilter) {
        if (selectedDayFilter == 0) shifts
        else shifts.filter { it.dayOfWeek == selectedDayFilter }
    }

    val totalHours = remember(shifts) {
        shifts.filter { it.isEnabled }.sumOf { it.durationHours }
    }

    val activeShiftsCount = remember(shifts) {
        shifts.count { it.isEnabled }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weekly Shifts", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("schedule_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.testTag("add_shift_action")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add Shift", tint = DelivioYellowDark)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = DelivioYellow,
                contentColor = TextOnYellow,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Shift", fontWeight = FontWeight.Bold) },
                modifier = Modifier.testTag("add_shift_fab")
            )
        },
        modifier = Modifier.testTag("schedule_screen")
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Personal Planner Info Banner
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DelivioYellow.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(1.dp, DelivioYellow.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth().testTag("schedule_planner_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(DelivioYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = TextOnYellow,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Personal Shift Planner",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Plan your weekly availability with optional 15-minute reminders. This planner is for your personal use and does not block or restrict you from going online anytime.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Weekly Summary & Presets Card
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Weekly Target",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${String.format(Locale.US, "%.1f", totalHours)} hrs planned",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DelivioYellow.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "$activeShiftsCount active shifts",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = DelivioYellowDark,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Quick Presets:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.applyShiftPreset("standard") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("preset_standard")
                            ) {
                                Text("Mon-Fri (8-5)", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { viewModel.applyShiftPreset("peak") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("preset_peak")
                            ) {
                                Text("Peak Hours", fontSize = 11.sp, maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { viewModel.applyShiftPreset("full_week") },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                modifier = Modifier.weight(1f).testTag("preset_full_week")
                            ) {
                                Text("Full Week", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 3. Day of Week Filter Chips
            item {
                val days = listOf(
                    0 to "All Days",
                    1 to "Mon",
                    2 to "Tue",
                    3 to "Wed",
                    4 to "Thu",
                    5 to "Fri",
                    6 to "Sat",
                    7 to "Sun"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("day_filter_row")
                ) {
                    items(days) { (dayNum, label) ->
                        val isSelected = selectedDayFilter == dayNum
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedDayFilter = dayNum },
                            label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DelivioYellow,
                                selectedLabelColor = TextOnYellow
                            )
                        )
                    }
                }
            }

            // 4. Shift List
            if (filteredShifts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.EventBusy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No shifts planned",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedDayFilter == 0) "Tap '+ Add Shift' to add your weekly working hours"
                                else "No shifts scheduled for this day",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(filteredShifts, key = { it.id }) { shift ->
                    ShiftCard(
                        shift = shift,
                        onToggle = { enabled -> viewModel.toggleShift(shift.id, enabled) },
                        onToggleReminder = { enabled -> viewModel.toggleShiftReminder(shift.id, enabled) },
                        onDelete = { viewModel.deleteShift(shift.id) }
                    )
                }
            }

            // Bottom spacer for FAB
            item {
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }

    // Add Shift Dialog
    if (showAddDialog) {
        AddShiftDialog(
            initialDay = if (selectedDayFilter in 1..7) selectedDayFilter else 1,
            onDismiss = { showAddDialog = false },
            onSave = { newShift ->
                viewModel.addShift(newShift)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ShiftCard(
    shift: DriverShift,
    onToggle: (Boolean) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (shift.isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (shift.isEnabled) 2.dp else 0.dp),
        border = BorderStroke(
            1.dp,
            if (shift.isEnabled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth().testTag("shift_card_${shift.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Day badge
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (shift.isEnabled) DelivioYellow.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(width = 52.dp, height = 48.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = shift.dayShortName.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        color = if (shift.isEnabled) DelivioYellowDark else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Time and reminder details
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${shift.startTime} – ${shift.endTime}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (shift.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", shift.durationHours)}h",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Reminder toggle chip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleReminder(!shift.reminder15MinBefore) }
                        .padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = if (shift.reminder15MinBefore) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                        contentDescription = null,
                        tint = if (shift.reminder15MinBefore && shift.isEnabled) DelivioYellowDark else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (shift.reminder15MinBefore) "Remind 15m before" else "No reminder",
                        fontSize = 11.sp,
                        fontWeight = if (shift.reminder15MinBefore) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (shift.reminder15MinBefore && shift.isEnabled) DelivioYellowDark else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // On/Off Switch
            Switch(
                checked = shift.isEnabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("shift_switch_${shift.id}")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp).testTag("delete_shift_${shift.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete shift",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AddShiftDialog(
    initialDay: Int,
    onDismiss: () -> Unit,
    onSave: (DriverShift) -> Unit
) {
    var selectedDay by remember { mutableIntStateOf(initialDay) }
    var startTime by remember { mutableStateOf("08:00") }
    var endTime by remember { mutableStateOf("17:00") }
    var reminder15Min by remember { mutableStateOf(true) }

    val days = listOf(
        1 to "Mon",
        2 to "Tue",
        3 to "Wed",
        4 to "Thu",
        5 to "Fri",
        6 to "Sat",
        7 to "Sun"
    )

    val timePresetsStart = listOf("06:00", "08:00", "10:00", "11:00", "13:00", "17:00", "18:00")
    val timePresetsEnd = listOf("12:00", "14:00", "17:00", "19:00", "21:00", "22:00", "23:00")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().testTag("add_shift_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Add Weekly Shift",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Configure day and hours for your personal planner",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Select Day
                Text("Day of Week", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    days.forEach { (d, label) ->
                        val isSel = selectedDay == d
                        Surface(
                            shape = CircleShape,
                            color = if (isSel) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(38.dp)
                                .clickable { selectedDay = d }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.Normal,
                                    color = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Start Time
                Text("Start Time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(timePresetsStart) { time ->
                        val isSel = startTime == time
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { startTime = time }
                        ) {
                            Text(
                                text = time,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // End Time
                Text("End Time", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(timePresetsEnd) { time ->
                        val isSel = endTime == time
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) DelivioYellow else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { endTime = time }
                        ) {
                            Text(
                                text = time,
                                fontSize = 12.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) TextOnYellow else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 15-min reminder toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Shift Reminder", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("Notify 15 minutes before shift starts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = reminder15Min,
                        onCheckedChange = { reminder15Min = it },
                        modifier = Modifier.testTag("add_shift_reminder_switch")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                DriverShift(
                                    dayOfWeek = selectedDay,
                                    startTime = startTime,
                                    endTime = endTime,
                                    reminder15MinBefore = reminder15Min,
                                    isEnabled = true
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DelivioYellow,
                            contentColor = TextOnYellow
                        ),
                        modifier = Modifier.testTag("save_shift_button")
                    ) {
                        Text("Save Shift", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
