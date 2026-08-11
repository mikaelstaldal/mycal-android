package nu.staldal.mycal.ui.event

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** How long an event becomes when its current length cannot be read off the form. */
private val DEFAULT_DURATION: Duration = Duration.ofHours(1)

/** The start time an event gets when it stops being all-day without ever having had one. */
private const val DEFAULT_START_TIME = "09:00"

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The four date/time strings of the event form, and the rules that keep them consistent.
 *
 * Editing either end of the range keeps it valid, so an edit can never leave the event ending
 * before it starts — the state the save refuses. A blank field does not stop that: the form reaches
 * such a state routinely, since an all-day event is loaded with no times at all, and a rule that
 * cannot read the event's current length falls back to a default one instead of giving up. An
 * *unreadable* field is different — it leaves the range alone rather than guessing at what was
 * meant.
 *
 * `endDate` is the *inclusive* last day, as the form shows it; the exclusive end the API wants is
 * derived when saving. Times are blank only until the event first becomes timed: toggling all-day
 * back on keeps them, so that toggling it off again restores what the user had. The `allDay`
 * parameter, not a blank time, is what says whether the times are part of the event.
 */
data class EventTimeRange(
    val startDate: String = "",
    val startTime: String = "",
    val endDate: String = "",
    val endTime: String = "",
)

/** Moves the start to [value], carrying the end along so the event keeps its length. */
fun EventTimeRange.withStartDate(value: String, allDay: Boolean): EventTimeRange =
    copy(startDate = value).followStart(previous = this, allDay = allDay)

/** Moves the start to [value], carrying the end along so the event keeps its length. */
fun EventTimeRange.withStartTime(value: String, allDay: Boolean): EventTimeRange =
    copy(startTime = value).followStart(previous = this, allDay = allDay)

/** Sets the end date, snapping it forward when it would land before the start. */
fun EventTimeRange.withEndDate(value: String, allDay: Boolean): EventTimeRange =
    copy(endDate = value).clampEnd(allDay)

/** Sets the end time, moving the end to the next day when that is what it takes to stay after the start. */
fun EventTimeRange.withEndTime(value: String, allDay: Boolean): EventTimeRange =
    copy(endTime = value).clampEnd(allDay)

/**
 * Switches the event between all-day and timed. A timed event needs times, so one that has none —
 * because it was all-day when the form loaded it — is given [DEFAULT_START_TIME] and a valid end
 * instead of being left with two empty time fields that the start/end coupling cannot work with.
 */
fun EventTimeRange.withAllDay(allDay: Boolean): EventTimeRange {
    if (allDay) return this
    val time = startTime.ifBlank { DEFAULT_START_TIME }
    val filled = copy(startTime = time, endTime = endTime.ifBlank { time })
    val start = parseDateTime(filled.startDate, filled.startTime) ?: return filled
    val end = parseDateTime(filled.endDate, filled.endTime)
    // An event spanning several days keeps spanning them, at the same time of day. One that would
    // last no time at all gets the default length, rather than the 24 hours that holding on to the
    // time of day would silently turn it into. Times kept from an earlier stint as a timed event
    // get the same treatment, since the dates may have moved under them in the meantime — and
    // unlike a time the user just picked, there is no choice here worth preserving.
    return if (end == null || !end.isAfter(start)) filled.withEnd(start.plus(DEFAULT_DURATION)) else filled
}

/** Snaps an end that is not after the start forward, for a range assembled from outside the form. */
fun EventTimeRange.normalized(allDay: Boolean): EventTimeRange = clampEnd(allDay)

/**
 * The dates and times a new event starts out with: the next full hour after [now], lasting
 * [DEFAULT_DURATION], unless an incoming intent's [prefill] says otherwise. Both ends are derived
 * from the same instant, so an event created late in the evening starts tomorrow rather than at
 * midnight today.
 *
 * The result is normalized, because a prefill comes from another app and may be inconsistent.
 */
fun newEventRange(prefill: NewEventPrefill?, now: LocalDateTime): EventTimeRange {
    val start = now.plusHours(1).truncatedTo(ChronoUnit.HOURS)
    val end = start.plus(DEFAULT_DURATION)
    return EventTimeRange(
        startDate = prefill?.startDate ?: start.toLocalDate().toString(),
        startTime = prefill?.startTime ?: start.toLocalTime().format(TIME_FORMAT),
        endDate = prefill?.endDate ?: prefill?.startDate ?: end.toLocalDate().toString(),
        endTime = prefill?.endTime ?: end.toLocalTime().format(TIME_FORMAT),
    ).normalized(prefill?.allDay == true)
}

/**
 * Puts the end back where the start left it: [previous] is the range as it was before the edit, and
 * the event keeps the length it had there. A length that cannot be read — the end is blank, or the
 * range was already inverted — becomes [DEFAULT_DURATION], so a start edit always leaves the end
 * both filled in and after the start.
 */
private fun EventTimeRange.followStart(previous: EventTimeRange, allDay: Boolean): EventTimeRange {
    val start = parseDateTime(startDate, startTime)
    // Without a start time there is no duration to preserve, only whole days — which is also the
    // all-day case, where the times are not part of the event at all.
    if (allDay || startTime.isBlank() || start == null) {
        val startDay = parseDate(startDate) ?: return this
        val days = previous.spanDays()?.coerceAtLeast(0L) ?: 0L
        return copy(endDate = startDay.plusDays(days).toString())
    }
    val duration = previous.duration()?.takeIf { it > Duration.ZERO } ?: DEFAULT_DURATION
    return withEnd(start.plus(duration))
}

/**
 * Pushes an end that lands on or before the start forward to the first day that keeps the range
 * valid, holding on to the clock time it was given — an event ending at 01:00 ends the next
 * morning, not an hour after it started.
 */
private fun EventTimeRange.clampEnd(allDay: Boolean): EventTimeRange {
    val startDay = parseDate(startDate) ?: return this
    if (allDay || startTime.isBlank() || endTime.isBlank()) {
        val endDay = parseDate(endDate) ?: return this
        return if (endDay.isBefore(startDay)) copy(endDate = startDate) else this
    }
    val start = parseDateTime(startDate, startTime) ?: return this
    var end = parseDateTime(endDate, endTime) ?: return this
    if (end.isAfter(start)) return this
    end = end.plusDays(ChronoUnit.DAYS.between(end.toLocalDate(), start.toLocalDate()))
    if (!end.isAfter(start)) end = end.plusDays(1)
    return withEnd(end)
}

private fun EventTimeRange.withEnd(end: LocalDateTime): EventTimeRange =
    copy(endDate = end.toLocalDate().toString(), endTime = end.toLocalTime().format(TIME_FORMAT))

/** Whole days from the start date to the end date, or null if either is unreadable. */
private fun EventTimeRange.spanDays(): Long? {
    val start = parseDate(startDate) ?: return null
    val end = parseDate(endDate) ?: return null
    return ChronoUnit.DAYS.between(start, end)
}

/** How long the event lasts, or null if either end of it is unreadable. */
private fun EventTimeRange.duration(): Duration? {
    val start = parseDateTime(startDate, startTime) ?: return null
    val end = parseDateTime(endDate, endTime) ?: return null
    return Duration.between(start, end)
}

private fun parseDate(date: String): LocalDate? =
    try {
        LocalDate.parse(date)
    } catch (_: DateTimeParseException) {
        null
    }

private fun parseDateTime(date: String, time: String): LocalDateTime? {
    val day = parseDate(date) ?: return null
    if (time.isBlank()) return day.atStartOfDay()
    return try {
        day.atTime(LocalTime.parse(time, TIME_FORMAT))
    } catch (_: DateTimeParseException) {
        null
    }
}
