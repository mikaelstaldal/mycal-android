package nu.staldal.mycal.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.ui.calendar.cssColorToComposeColor
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class ColorOption(val name: String, val color: Color)

val REMINDER_OPTIONS = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

val EVENT_COLORS = listOf(
    ColorOption("", Color(0xFF9E9E9E)),
    ColorOption("dodgerblue", Color(0xFF1E90FF)),
    ColorOption("red", Color(0xFFFF0000)),
    ColorOption("gold", Color(0xFFFFD700)),
    ColorOption("green", Color(0xFF008000)),
    ColorOption("orange", Color(0xFFFFA500)),
    ColorOption("mediumturquoise", Color(0xFF48D1CC)),
    ColorOption("cornflowerblue", Color(0xFF6495ED)),
    ColorOption("salmon", Color(0xFFFA8072)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormScreen(
    eventId: Long?, // null for create, non-null for edit
    onNavigateBack: () -> Unit,
    viewModel: EventViewModel = viewModel(),
) {
    val state by viewModel.formState.collectAsState()
    val isEdit = eventId != null

    LaunchedEffect(eventId) {
        if (eventId != null) {
            viewModel.loadEventForEdit(eventId)
        } else {
            // Set default dates for new event
            val today = LocalDate.now()
            val now = LocalTime.now()
            val startHour = now.plusHours(1).withMinute(0)
            val endHour = startHour.plusHours(1)
            viewModel.updateStartDate(today.toString())
            viewModel.updateEndDate(today.toString())
            viewModel.updateStartTime(startHour.format(DateTimeFormatter.ofPattern("HH:mm")))
            viewModel.updateEndTime(endHour.format(DateTimeFormatter.ofPattern("HH:mm")))
        }
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Event" else "New Event") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isEdit) viewModel.updateEvent(eventId!!)
                            else viewModel.createEvent()
                        },
                        enabled = !state.isSaving && state.title.isNotBlank(),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.error != null && state.title.isBlank(),
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )

            OutlinedTextField(
                value = state.location,
                onValueChange = { viewModel.updateLocation(it) },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("All day")
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = state.allDay,
                    onCheckedChange = { viewModel.updateAllDay(it) },
                )
            }

            // Date fields
            OutlinedTextField(
                value = state.startDate,
                onValueChange = { viewModel.updateStartDate(it) },
                label = { Text("Start Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (!state.allDay) {
                OutlinedTextField(
                    value = state.startTime,
                    onValueChange = { viewModel.updateStartTime(it) },
                    label = { Text("Start Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            OutlinedTextField(
                value = state.endDate,
                onValueChange = { viewModel.updateEndDate(it) },
                label = { Text("End Date (yyyy-MM-dd)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            if (!state.allDay) {
                OutlinedTextField(
                    value = state.endTime,
                    onValueChange = { viewModel.updateEndTime(it) },
                    label = { Text("End Time (HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }

            // Reminder picker
            ReminderPicker(
                selectedMinutes = state.reminderMinutes,
                onMinutesSelected = { viewModel.updateReminderMinutes(it) },
            )

            // Color picker
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EVENT_COLORS.forEach { colorOpt ->
                    val isSelected = state.color == colorOpt.name
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colorOpt.color)
                            .then(
                                if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else Modifier
                            )
                            .clickable { viewModel.updateColor(colorOpt.name) },
                    )
                }
            }

            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }

            if (state.isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPicker(
    selectedMinutes: Int,
    onMinutesSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = NotificationScheduler.formatReminderMinutes(selectedMinutes),
            onValueChange = {},
            readOnly = true,
            label = { Text("Reminder") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            REMINDER_OPTIONS.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text(NotificationScheduler.formatReminderMinutes(minutes)) },
                    onClick = {
                        onMinutesSelected(minutes)
                        expanded = false
                    },
                )
            }
        }
    }
}
