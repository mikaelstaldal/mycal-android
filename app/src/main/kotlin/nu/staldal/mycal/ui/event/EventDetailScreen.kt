package nu.staldal.mycal.ui.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.ui.calendar.cssColorToComposeColor
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: EventViewModel = viewModel(),
) {
    val state by viewModel.detailState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditSeriesDialog by remember { mutableStateOf(false) }
    var showDeleteSeriesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(eventId) {
        viewModel.loadEvent(eventId)
    }

    LaunchedEffect(state.isDeleted) {
        if (state.isDeleted) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val event = state.event
                        if (event?.parentId != null) {
                            showEditSeriesDialog = true
                        } else {
                            onNavigateToEdit(eventId)
                        }
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = {
                        val event = state.event
                        if (event?.parentId != null) {
                            showDeleteSeriesDialog = true
                        } else {
                            showDeleteDialog = true
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            state.event != null -> {
                val event = state.event!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(cssColorToComposeColor(event.color)),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(event.title, style = MaterialTheme.typography.headlineSmall)
                    }

                    if (event.allDay) {
                        DetailRow("Date", DateUtils.formatDisplayDate(event.startTime))
                    } else {
                        DetailRow("Start", DateUtils.formatDisplayDateTime(event.startTime))
                        DetailRow("End", DateUtils.formatDisplayDateTime(event.endTime))
                    }

                    if (event.location.isNotBlank() || (event.latitude != null && event.longitude != null)) {
                        val context = LocalContext.current
                        val hasCoordinates = event.latitude != null && event.longitude != null
                        val displayText = if (event.location.isNotBlank()) {
                            event.location
                        } else {
                            "${event.latitude}, ${event.longitude}"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val geoUri = if (hasCoordinates) {
                                    val label = Uri.encode(displayText)
                                    Uri.parse("geo:${event.latitude},${event.longitude}?q=${event.latitude},${event.longitude}($label)")
                                } else {
                                    val encodedLocation = Uri.encode(event.location)
                                    Uri.parse("geo:0,0?q=$encodedLocation")
                                }
                                val intent = Intent(Intent.ACTION_VIEW, geoUri)
                                try {
                                    context.startActivity(Intent.createChooser(intent, "Open in map"))
                                } catch (_: ActivityNotFoundException) {
                                    // No map app installed
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = "Open in map",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Location: ",
                                style = MaterialTheme.typography.labelLarge,
                            )
                            Text(
                                text = displayText,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    if (event.description.isNotBlank()) {
                        Text("Description", style = MaterialTheme.typography.labelLarge)
                        Text(htmlToAnnotatedString(event.description))
                    }

                    if (event.url.isNotBlank()) {
                        val context = LocalContext.current
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(event.url))
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    // No app to handle URL
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Link,
                                contentDescription = "Open URL",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = event.url,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = TextDecoration.Underline,
                            )
                        }
                    }

                    if (event.reminderMinutes > 0) {
                        DetailRow("Reminder", NotificationScheduler.formatReminderMinutes(event.reminderMinutes))
                    }

                    if (event.recurrenceFreq.isNotBlank()) {
                        DetailRow("Recurrence", formatRecurrenceInfo(event))
                    }

                    if (event.parentId != null) {
                        Text(
                            "Part of recurring series",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // Simple delete dialog (non-recurring events or parent events)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Event") },
            text = { Text("Are you sure you want to delete this event?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.deleteEvent(eventId)
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }

    // Edit series dialog (recurring instances)
    if (showEditSeriesDialog) {
        val parentId = state.event?.parentId
        AlertDialog(
            onDismissRequest = { showEditSeriesDialog = false },
            title = { Text("Edit Recurring Event") },
            text = { Text("Do you want to edit this event or all events in the series?") },
            confirmButton = {
                TextButton(onClick = {
                    showEditSeriesDialog = false
                    onNavigateToEdit(eventId)
                }) {
                    Text("This event")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showEditSeriesDialog = false }) {
                        Text("Cancel")
                    }
                    if (parentId != null) {
                        TextButton(onClick = {
                            showEditSeriesDialog = false
                            onNavigateToEdit(parentId)
                        }) {
                            Text("All events")
                        }
                    }
                }
            },
        )
    }

    // Delete series dialog (recurring instances)
    if (showDeleteSeriesDialog) {
        val parentId = state.event?.parentId
        AlertDialog(
            onDismissRequest = { showDeleteSeriesDialog = false },
            title = { Text("Delete Recurring Event") },
            text = { Text("Do you want to delete this event or all events in the series?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteSeriesDialog = false
                    viewModel.deleteEvent(eventId)
                }) {
                    Text("This event", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showDeleteSeriesDialog = false }) {
                        Text("Cancel")
                    }
                    if (parentId != null) {
                        TextButton(onClick = {
                            showDeleteSeriesDialog = false
                            viewModel.deleteEvent(parentId)
                        }) {
                            Text("All events", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.labelLarge,
        )
        Text(value)
    }
}

private fun formatRecurrenceInfo(event: EventDto): String {
    val freq = event.recurrenceFreq.lowercase().replaceFirstChar { it.uppercase() }
    val interval = event.recurrenceInterval
    val base = if (interval != null && interval > 1) {
        val unit = when (event.recurrenceFreq.lowercase()) {
            "daily" -> "days"
            "weekly" -> "weeks"
            "monthly" -> "months"
            "yearly" -> "years"
            else -> event.recurrenceFreq.lowercase()
        }
        "Every $interval $unit"
    } else {
        freq
    }

    val end = when {
        event.recurrenceCount != null -> ", ${event.recurrenceCount} times"
        event.recurrenceUntil != null -> ", until ${event.recurrenceUntil}"
        else -> ""
    }

    val days = if (event.recurrenceByDay != null) " on ${event.recurrenceByDay}" else ""

    return "$base$days$end"
}

private fun htmlToAnnotatedString(html: String): AnnotatedString {
    val spanned = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT)
    return buildAnnotatedString {
        append(spanned.toString())
        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            when (span) {
                is android.text.style.StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                    android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                }
                is android.text.style.UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                is android.text.style.StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
            }
        }
    }
}
