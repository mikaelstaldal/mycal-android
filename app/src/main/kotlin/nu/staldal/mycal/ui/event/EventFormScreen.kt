package nu.staldal.mycal.ui.event

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.content.ActivityNotFoundException
import nu.staldal.mycal.data.api.MyNotesClient
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.ui.calendar.cssColorToComposeColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

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

val RECURRENCE_FREQ_OPTIONS = listOf("", "DAILY", "WEEKLY", "MONTHLY", "YEARLY")

data class WeekdayOption(val code: String, val label: String)

val WEEKDAY_OPTIONS = listOf(
    WeekdayOption("MO", "Mon"),
    WeekdayOption("TU", "Tue"),
    WeekdayOption("WE", "Wed"),
    WeekdayOption("TH", "Thu"),
    WeekdayOption("FR", "Fri"),
    WeekdayOption("SA", "Sat"),
    WeekdayOption("SU", "Sun"),
)

/** Pre-fill values for a new event, e.g. from an incoming `ACTION_INSERT` intent. */
data class NewEventPrefill(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val startDate: String? = null, // yyyy-MM-dd
    val startTime: String? = null, // HH:mm
    val endDate: String? = null,   // yyyy-MM-dd
    val endTime: String? = null,   // HH:mm
    val allDay: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventFormScreen(
    eventId: String?, // null for create, non-null for edit
    onNavigateBack: () -> Unit,
    prefill: NewEventPrefill? = null,
    viewModel: EventViewModel = viewModel(),
) {
    val state by viewModel.formState.collectAsState()
    val isEdit = eventId != null
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(eventId) {
        if (eventId != null) {
            viewModel.loadEventForEdit(eventId)
        } else {
            // Set default dates for new event
            val today = LocalDate.now()
            val now = LocalTime.now()
            val startHour = now.plusHours(1).withMinute(0)
            val endHour = startHour.plusHours(1)
            val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            viewModel.updateStartDate(prefill?.startDate ?: today.toString())
            viewModel.updateEndDate(prefill?.endDate ?: prefill?.startDate ?: today.toString())
            viewModel.updateStartTime(prefill?.startTime ?: startHour.format(timeFormatter))
            viewModel.updateEndTime(prefill?.endTime ?: endHour.format(timeFormatter))
            if (prefill?.allDay == true) {
                viewModel.updateAllDay(true)
            }
            prefill?.title?.let { viewModel.updateTitle(it) }
            prefill?.description?.let { viewModel.updateDescription(it) }
            prefill?.location?.let { viewModel.updateLocation(it) }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) {
            onNavigateBack()
        }
    }

    if (!isEdit) {
        LaunchedEffect(Unit) {
            titleFocusRequester.requestFocus()
        }
    }

    Scaffold(
        // Under edge-to-edge the window is not resized by the keyboard, so without the ime inset
        // the form would keep its full height and the field being edited could end up behind the
        // keyboard. Adding it shrinks the scrolling area to the space above the keyboard instead,
        // and the focused field is scrolled back into view.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            keyboardController?.hide()
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
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester),
                singleLine = true,
                isError = state.error != null && state.title.isBlank(),
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
            DatePickerField(
                value = state.startDate,
                label = "Start Date",
                onDateSelected = { viewModel.updateStartDate(it) },
                isError = state.error != null && state.startDate.isBlank(),
            )

            if (!state.allDay) {
                TimePickerField(
                    value = state.startTime,
                    label = "Start Time",
                    onTimeSelected = { viewModel.updateStartTime(it) },
                    isError = state.error != null && state.startTime.isBlank(),
                )
            }

            DatePickerField(
                value = state.endDate,
                label = "End Date",
                onDateSelected = { viewModel.updateEndDate(it) },
                isError = state.error != null && state.endDate.isBlank(),
            )

            if (!state.allDay) {
                TimePickerField(
                    value = state.endTime,
                    label = "End Time",
                    onTimeSelected = { viewModel.updateEndTime(it) },
                    isError = state.error != null && state.endTime.isBlank(),
                )
            }

            // Recurrence picker — hide for recurring instance edits
            if (!state.isRecurringInstance) {
                RecurrencePicker(
                    freq = state.recurrenceFreq,
                    interval = state.recurrenceInterval,
                    count = state.recurrenceCount,
                    until = state.recurrenceUntil,
                    byDay = state.recurrenceByDay,
                    onFreqChanged = { viewModel.updateRecurrenceFreq(it) },
                    onIntervalChanged = { viewModel.updateRecurrenceInterval(it) },
                    onCountChanged = { viewModel.updateRecurrenceCount(it) },
                    onUntilChanged = { viewModel.updateRecurrenceUntil(it) },
                    onByDayChanged = { viewModel.updateRecurrenceByDay(it) },
                )
            }

            // Reminder picker
            ReminderPicker(
                selectedMinutes = state.reminderMinutes,
                onMinutesSelected = { viewModel.updateReminderMinutes(it) },
            )

            LocationAutocompleteField(viewModel = viewModel, location = state.location)

            // Color picker
            Text("Color", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EVENT_COLORS.forEach { colorOpt ->
                    val isSelected = state.color == colorOpt.name
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(colorOpt.name.ifEmpty { "default" })
                            }
                        },
                        state = tooltipState,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(colorOpt.color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .semantics {
                                    contentDescription = "${colorOpt.name.ifEmpty { "default" }}${if (isSelected) ", selected" else ""}"
                                }
                                .clickable { viewModel.updateColor(colorOpt.name) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.url,
                onValueChange = { viewModel.updateUrl(it) },
                label = { Text("URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                isError = state.urlError,
                supportingText = if (state.urlError) {
                    { Text("Must be a valid http:// or https:// URL") }
                } else null,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
            )

            // MyNotes note link — only when the MyNotes app is installed and readable. An event
            // that already links a note keeps the picker, so the link can still be removed.
            if (state.mynotesAvailability.isAvailable || state.noteSlug.isNotBlank()) {
                NotePicker(viewModel = viewModel, noteSlug = state.noteSlug)
            }

            state.error?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurrencePicker(
    freq: String,
    interval: Int,
    count: Int?,
    until: String?,
    byDay: String?,
    onFreqChanged: (String) -> Unit,
    onIntervalChanged: (Int) -> Unit,
    onCountChanged: (Int?) -> Unit,
    onUntilChanged: (String?) -> Unit,
    onByDayChanged: (String?) -> Unit,
) {
    var freqExpanded by remember { mutableStateOf(false) }

    // Frequency dropdown
    ExposedDropdownMenuBox(
        expanded = freqExpanded,
        onExpandedChange = { freqExpanded = it },
    ) {
        OutlinedTextField(
            value = when (freq.lowercase()) {
                "daily" -> "Daily"
                "weekly" -> "Weekly"
                "monthly" -> "Monthly"
                "yearly" -> "Yearly"
                else -> "None"
            },
            onValueChange = {},
            readOnly = true,
            label = { Text("Repeat") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = freqExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = freqExpanded,
            onDismissRequest = { freqExpanded = false },
        ) {
            RECURRENCE_FREQ_OPTIONS.forEach { option ->
                DropdownMenuItem(
                    text = { Text(when (option) {
                        "DAILY" -> "Daily"
                        "WEEKLY" -> "Weekly"
                        "MONTHLY" -> "Monthly"
                        "YEARLY" -> "Yearly"
                        else -> "None"
                    }) },
                    onClick = {
                        onFreqChanged(option)
                        freqExpanded = false
                    },
                )
            }
        }
    }

    // Show additional options when recurrence is set
    if (freq.isNotBlank()) {
        val unitLabel = when (freq.lowercase()) {
            "daily" -> "days"
            "weekly" -> "weeks"
            "monthly" -> "months"
            "yearly" -> "years"
            else -> freq
        }

        // Interval
        OutlinedTextField(
            value = interval.toString(),
            onValueChange = { text ->
                val parsed = text.filter { it.isDigit() }.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    onIntervalChanged(parsed)
                }
            },
            label = { Text("Every N $unitLabel") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        // End condition
        val endMode = when {
            count != null -> "count"
            until != null -> "until"
            else -> "never"
        }

        Text("Ends", style = MaterialTheme.typography.labelLarge)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = endMode == "never",
                    onClick = { onCountChanged(null); onUntilChanged(null) },
                )
                Text("Never")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = endMode == "count",
                    onClick = { onCountChanged(count ?: 10) },
                )
                Text("After")
                if (endMode == "count") {
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = (count ?: 10).toString(),
                        onValueChange = { text ->
                            val parsed = text.filter { it.isDigit() }.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                onCountChanged(parsed)
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("occurrences")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = endMode == "until",
                    onClick = { onUntilChanged(until ?: LocalDate.now().plusMonths(3).toString()) },
                )
                Text("On date")
                if (endMode == "until") {
                    Spacer(modifier = Modifier.width(8.dp))
                    DatePickerField(
                        value = until ?: "",
                        label = "Until",
                        onDateSelected = { onUntilChanged(it) },
                    )
                }
            }
        }

        // Weekday selector (weekly only)
        if (freq.lowercase() == "weekly") {
            Text("On days", style = MaterialTheme.typography.labelLarge)
            val selectedDays = byDay?.split(",")?.map { it.trim() }?.toSet() ?: emptySet()
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                WEEKDAY_OPTIONS.forEach { day ->
                    val isSelected = day.code in selectedDays
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val newDays = if (isSelected) {
                                selectedDays - day.code
                            } else {
                                selectedDays + day.code
                            }
                            onByDayChanged(newDays.joinToString(",").ifBlank { null })
                        },
                        label = { Text(day.label) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    label: String,
    onDateSelected: (String) -> Unit,
    isError: Boolean = false,
) {
    var showDialog by remember { mutableStateOf(false) }

    val initialMillis = try {
        LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        singleLine = true,
        isError = isError,
        interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        showDialog = true
                    }
                }
            }
        },
    )

    if (showDialog) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onDateSelected(date.toString())
                    }
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    value: String,
    label: String,
    onTimeSelected: (String) -> Unit,
    isError: Boolean = false,
) {
    var showDialog by remember { mutableStateOf(false) }

    val (initialHour, initialMinute) = try {
        val time = LocalTime.parse(value, DateTimeFormatter.ofPattern("HH:mm"))
        time.hour to time.minute
    } catch (_: DateTimeParseException) {
        0 to 0
    }

    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        singleLine = true,
        isError = isError,
        interactionSource = remember { MutableInteractionSource() }.also { interactionSource ->
            LaunchedEffect(interactionSource) {
                interactionSource.interactions.collect { interaction ->
                    if (interaction is PressInteraction.Release) {
                        showDialog = true
                    }
                }
            }
        },
    )

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    val formatted = String.format(Locale.ROOT, "%02d:%02d", timePickerState.hour, timePickerState.minute)
                    onTimeSelected(formatted)
                    showDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            },
            title = { Text(label) },
            text = {
                TimePicker(state = timePickerState)
            },
        )
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
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
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

/**
 * Links one MyNotes note to the event. An event stores just the note's slug; the note itself stays
 * in the MyNotes app on this device, which is also where the titles searched here come from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NotePicker(
    viewModel: EventViewModel,
    noteSlug: String,
) {
    val context = LocalContext.current
    if (noteSlug.isNotBlank()) {
        Text("Note", style = MaterialTheme.typography.labelLarge)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = noteSlug,
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.weight(1f).clickable {
                    try {
                        context.startActivity(
                            MyNotesClient.viewIntent(context, MyNotesClient.noteUri(noteSlug))
                        )
                    } catch (_: ActivityNotFoundException) {
                        // MyNotes went away between the check and the tap
                    }
                },
            )
            TextButton(onClick = { viewModel.unlinkNote() }) {
                Text("Unlink", color = MaterialTheme.colorScheme.error)
            }
        }
        return
    }

    val query by viewModel.noteQuery.collectAsState()
    val suggestions by viewModel.noteSuggestions.collectAsState()

    ExposedDropdownMenuBox(
        expanded = suggestions.isNotEmpty(),
        onExpandedChange = { /* controlled by suggestions */ },
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.updateNoteQuery(it) },
            label = { Text("Note") },
            placeholder = { Text("Search MyNotes by title…") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            supportingText = if (query.isNotBlank() && suggestions.isEmpty()) {
                { Text("No matching note in MyNotes") }
            } else null,
        )
        if (suggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = true,
                onDismissRequest = { viewModel.updateNoteQuery("") },
            ) {
                suggestions.forEach { note ->
                    DropdownMenuItem(
                        text = { Text(note.title, maxLines = 2) },
                        onClick = { viewModel.selectNoteSuggestion(note) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationAutocompleteField(
    viewModel: EventViewModel,
    location: String,
) {
    val suggestions by viewModel.locationSuggestions.collectAsState()
    val locationError by viewModel.locationError.collectAsState()
    val expanded = suggestions.isNotEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { /* controlled by suggestions */ },
    ) {
        OutlinedTextField(
            value = location,
            onValueChange = { viewModel.updateLocation(it) },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            singleLine = true,
            isError = locationError != null,
            supportingText = locationError?.let { { Text(it) } },
        )
        if (expanded) {
            ExposedDropdownMenu(
                expanded = true,
                onDismissRequest = { viewModel.clearLocationSuggestions() },
            ) {
                suggestions.forEach { place ->
                    DropdownMenuItem(
                        text = { Text(place.display_name, maxLines = 2) },
                        onClick = { viewModel.selectLocationSuggestion(place) },
                    )
                }
            }
        }
    }
}
