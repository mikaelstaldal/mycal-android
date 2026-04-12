package nu.staldal.mycal.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.util.DateUtils
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val DATE_COLUMN_WIDTH_DP = 56

data class ScheduleDayItem(
    val date: LocalDate,
    val events: List<EventDto>,
)

@Composable
fun ScheduleContent(
    state: CalendarUiState,
    onEventClick: (String) -> Unit,
    onLoadMore: (Boolean) -> Unit,
    scrollToTodayTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val dayItems = remember(state.scheduleEvents, state.scheduleStartMonth, state.scheduleEndMonth) {
        buildScheduleDays(state)
    }

    val listState = rememberLazyListState()

    // Scroll to today when triggered
    LaunchedEffect(scrollToTodayTrigger) {
        if (scrollToTodayTrigger > 0) {
            val today = LocalDate.now()
            val todayIndex = dayItems.indexOfFirst { it.date >= today }
            if (todayIndex >= 0) {
                // Calculate the flat item index (sum of events in earlier days)
                val flatIndex = dayItems.take(todayIndex).sumOf { it.events.size }
                listState.scrollToItem(flatIndex)
            } else {
                listState.scrollToItem(0)
            }
        }
    }

    // Auto-load more when near edges
    val shouldLoadNext by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= listState.layoutInfo.totalItemsCount - 5
        }
    }
    val shouldLoadPrevious by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            firstVisible <= 5
        }
    }

    LaunchedEffect(shouldLoadNext) {
        if (shouldLoadNext && !state.isLoadingMore && dayItems.isNotEmpty()) {
            onLoadMore(true)
        }
    }

    LaunchedEffect(shouldLoadPrevious) {
        if (shouldLoadPrevious && !state.isLoadingMore && dayItems.isNotEmpty()) {
            onLoadMore(false)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (state.isLoadingMore && !state.isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
        }

        dayItems.forEach { dayItem ->
            val eventCount = dayItem.events.size
            dayItem.events.forEachIndexed { index, event ->
                item(key = "event-${dayItem.date}-${event.id}") {
                    ScheduleEventRow(
                        date = dayItem.date,
                        event = event,
                        showDate = index == 0,
                        isLastInDay = index == eventCount - 1,
                        onClick = { onEventClick(event.id) },
                        defaultEventColor = cssColorToComposeColor(state.defaultEventColor),
                        calendarColors = state.calendarColors,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleEventRow(
    date: LocalDate,
    event: EventDto,
    showDate: Boolean,
    isLastInDay: Boolean,
    onClick: () -> Unit,
    defaultEventColor: Color = Color(0xFF1E90FF),
    calendarColors: Map<Int, String> = emptyMap(),
) {
    val today = LocalDate.now()
    val isToday = date == today

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLastInDay) 8.dp else 0.dp),
    ) {
        // Event card — measured normally, determines the row height
        ScheduleEventItem(
            event = event,
            onClick = onClick,
            defaultEventColor = defaultEventColor,
            calendarColors = calendarColors,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = DATE_COLUMN_WIDTH_DP.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        )

        // Date column — uses matchParentSize so it doesn't expand the row
        if (showDate) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.TopStart,
            ) {
                Column(
                    modifier = Modifier.width(DATE_COLUMN_WIDTH_DP.dp).padding(top = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Box(
                        modifier = if (isToday) {
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        } else {
                            Modifier.size(36.dp)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEventItem(
    event: EventDto,
    onClick: () -> Unit,
    defaultEventColor: Color = Color(0xFF1E90FF),
    calendarColors: Map<Int, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val past = isEventPast(event)
    val bgColor = cssColorToComposeColor(effectiveEventColor(event, calendarColors), defaultEventColor).let {
        if (past) it.copy(alpha = 0.4f) else it
    }
    val contentColor = Color.White

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier.clickable(onClick = onClick),
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

private fun buildScheduleDays(state: CalendarUiState): List<ScheduleDayItem> {
    val today = LocalDate.now()
    val monthStart = state.scheduleStartMonth.atDay(1)
    val currentMonthStart = YearMonth.now().atDay(1)
    val startDate = if (monthStart == currentMonthStart) today else monthStart
    val endDate = state.scheduleEndMonth.atEndOfMonth()

    val eventsByDate = mutableMapOf<LocalDate, MutableList<EventDto>>()
    state.scheduleEvents.forEach { event ->
        val eventStart = DateUtils.parseToLocalDate(event.startTime) ?: return@forEach
        if (event.allDay && event.endTime.isNotBlank()) {
            val eventEnd = DateUtils.parseToLocalDate(event.endTime) ?: eventStart.plusDays(1)
            var d = eventStart
            while (d.isBefore(eventEnd)) {
                eventsByDate.getOrPut(d) { mutableListOf() }.add(event)
                d = d.plusDays(1)
            }
        } else {
            eventsByDate.getOrPut(eventStart) { mutableListOf() }.add(event)
        }
    }

    val days = mutableListOf<ScheduleDayItem>()
    var date = startDate
    while (!date.isAfter(endDate)) {
        val dayEvents = (eventsByDate[date] ?: emptyList()).sortedWith(
            compareBy<EventDto> { !it.allDay }
                .thenBy { it.startTime }
        )
        if (dayEvents.isNotEmpty()) {
            days.add(ScheduleDayItem(date = date, events = dayEvents))
        }
        date = date.plusDays(1)
    }
    return days
}
