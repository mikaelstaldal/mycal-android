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
import nu.staldal.mycal.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventDetailScreen(
    eventId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    viewModel: EventViewModel = viewModel(),
) {
    val state by viewModel.detailState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

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
                    IconButton(onClick = { onNavigateToEdit(eventId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
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

                    if (event.reminderMinutes > 0) {
                        DetailRow("Reminder", NotificationScheduler.formatReminderMinutes(event.reminderMinutes))
                    }

                    if (event.recurrenceFreq.isNotBlank()) {
                        DetailRow("Recurrence", event.recurrenceFreq.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }
    }

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
