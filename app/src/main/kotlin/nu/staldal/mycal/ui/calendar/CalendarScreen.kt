package nu.staldal.mycal.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.util.DateUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToEvent: (Long) -> Unit,
    onNavigateToNewEvent: () -> Unit,
    forceScheduleView: Boolean = false,
    viewModel: CalendarViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showSearch by remember { mutableStateOf(false) }

    LaunchedEffect(forceScheduleView) {
        if (forceScheduleView && state.viewMode != ViewMode.SCHEDULE) {
            viewModel.setViewMode(ViewMode.SCHEDULE)
        }
    }

    if (!state.isConfigured) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Welcome to MyCal", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Configure your server to get started")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onNavigateToSettings) {
                    Text("Open Settings")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { viewModel.enableOfflineMode() }) {
                    Text("Work Offline")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            if (showSearch) {
                SearchBar(
                    query = state.searchQuery,
                    onQueryChange = { viewModel.search(it) },
                    onClose = {
                        showSearch = false
                        viewModel.clearSearch()
                    },
                )
            } else {
                TopAppBar(
                    title = { Text("MyCal") },
                    actions = {
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            if (state.viewMode == ViewMode.SCHEDULE) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = "Month view")
                            } else {
                                Icon(Icons.AutoMirrored.Default.ViewList, contentDescription = "Schedule view")
                            }
                        }
                        IconButton(onClick = { showSearch = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                        if (!state.isOfflineMode && state.isOnline) {
                            if (state.pendingChangesCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge { Text("${state.pendingChangesCount}") }
                                    },
                                ) {
                                    IconButton(onClick = { viewModel.syncNow() }) {
                                        Icon(Icons.Default.Sync, contentDescription = "Sync now")
                                    }
                                }
                            } else {
                                IconButton(onClick = { viewModel.syncNow() }) {
                                    Icon(Icons.Default.Sync, contentDescription = "Sync now")
                                }
                            }
                        }
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (!showSearch) {
                FloatingActionButton(onClick = onNavigateToNewEvent) {
                    Icon(Icons.Default.Add, contentDescription = "New Event")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!state.isOfflineMode && !state.isOnline) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Offline",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            if (showSearch && state.searchQuery.isNotBlank()) {
                SearchResults(
                    results = state.searchResults,
                    isSearching = state.isSearching,
                    onEventClick = onNavigateToEvent,
                )
            } else {
                if (state.viewMode == ViewMode.SCHEDULE) {
                    ScheduleContent(
                        state = state,
                        onEventClick = onNavigateToEvent,
                        onLoadMore = { loadNext -> viewModel.loadMoreScheduleEvents(loadNext) },
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        MonthHeader(
                            month = state.currentMonth,
                            onPrevious = { viewModel.previousMonth() },
                            onNext = { viewModel.nextMonth() },
                        )
                        CalendarGrid(
                            month = state.currentMonth,
                            selectedDate = state.selectedDate,
                            events = state.events,
                            onDateSelected = { viewModel.selectDate(it) },
                        )
                        HorizontalDivider()
                        DayEventList(
                                date = state.selectedDate,
                                events = state.selectedDayEvents,
                                onEventClick = onNavigateToEvent,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            state.error?.let { error ->
            Snackbar(
                modifier = Modifier.padding(padding).padding(16.dp),
            ) {
                Text(error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    TopAppBar(
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search events...") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close search")
            }
        },
    )
}

@Composable
private fun SearchResults(
    results: List<EventDto>,
    isSearching: Boolean,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isSearching) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (results.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No results found")
        }
    } else {
        LazyColumn(modifier = modifier.fillMaxSize()) {
            items(results) { event ->
                EventListItem(event = event, onClick = { onEventClick(event.id) })
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
        }
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleLarge,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    selectedDate: LocalDate,
    events: List<EventDto>,
    onDateSelected: (LocalDate) -> Unit,
) {
    val daysOfWeek = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val firstDayOfMonth = month.atDay(1)
    val dayOfWeekOffset = (firstDayOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    val today = LocalDate.now()

    // Build set of dates that have events
    val eventDates = events.mapNotNull { DateUtils.parseToLocalDate(it.startTime) }.toSet()
    // Build map of date to first event color
    val eventColors = events.mapNotNull { event ->
        val date = DateUtils.parseToLocalDate(event.startTime)
        if (date != null) date to event.color else null
    }.groupBy({ it.first }, { it.second })

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Day-of-week headers
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Calendar days
        val totalCells = dayOfWeekOffset + daysInMonth
        val rows = (totalCells + 6) / 7
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - dayOfWeekOffset + 1
                    if (dayNum in 1..daysInMonth) {
                        val date = month.atDay(dayNum)
                        val isSelected = date == selectedDate
                        val isToday = date == today
                        val hasEvents = date in eventDates
                        val colors = eventColors[date] ?: emptyList()

                        DayCell(
                            day = dayNum,
                            isSelected = isSelected,
                            isToday = isToday,
                            hasEvents = hasEvents,
                            eventColor = colors.firstOrNull { it.isNotBlank() },
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    eventColor: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = day.toString(),
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        if (hasEvents) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(cssColorToComposeColor(eventColor)),
            )
        }
    }
}

@Composable
private fun DayEventList(
    date: LocalDate,
    events: List<EventDto>,
    onEventClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = date.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d")),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        if (events.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("No events", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn {
                items(events) { event ->
                    EventListItem(event = event, onClick = { onEventClick(event.id) })
                }
            }
        }
    }
}

@Composable
fun EventListItem(
    event: EventDto,
    onClick: () -> Unit,
) {
    val bgColor = cssColorToComposeColor(event.color)
    val contentColor = Color.White

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!event.allDay) {
                Text(
                    text = "${DateUtils.formatDisplayTime(event.startTime)} - ${DateUtils.formatDisplayTime(event.endTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f),
                )
            }
        }
    }
}

val DEFAULT_EVENT_COLOR = Color(0xFF1E90FF) // dodgerblue

fun cssColorToComposeColor(name: String?): Color {
    return when (name?.lowercase()) {
        "dodgerblue" -> Color(0xFF1E90FF)
        "red" -> Color(0xFFFF0000)
        "gold" -> Color(0xFFFFD700)
        "green" -> Color(0xFF008000)
        "orange" -> Color(0xFFFFA500)
        "mediumturquoise" -> Color(0xFF48D1CC)
        "cornflowerblue" -> Color(0xFF6495ED)
        "salmon" -> Color(0xFFFA8072)
        else -> DEFAULT_EVENT_COLOR
    }
}
