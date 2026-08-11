package nu.staldal.mycal.ui.event

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * Covers the start/end coupling of the event form. The property every case here checks is the same
 * one the save enforces and the server rejects a violation of: after any single edit the event
 * still ends after it starts, and the end is still filled in.
 */
class EventTimeRangeTest {

    private fun timed(
        startDate: String = "2026-08-11",
        startTime: String = "10:00",
        endDate: String = "2026-08-11",
        endTime: String = "11:00",
    ) = EventTimeRange(startDate, startTime, endDate, endTime)

    private fun allDay(startDate: String = "2026-08-11", endDate: String = "2026-08-11") =
        EventTimeRange(startDate = startDate, endDate = endDate)

    // --- moving the start ----------------------------------------------------

    @Test
    fun `moving the start date carries the end date with it`() {
        assertEquals(
            timed(startDate = "2026-09-01", endDate = "2026-09-01"),
            timed().withStartDate("2026-09-01", allDay = false),
        )
    }

    @Test
    fun `moving the start date keeps a multi-day event the same length`() {
        val range = timed(startDate = "2026-08-11", endDate = "2026-08-14")
        assertEquals(
            timed(startDate = "2026-08-20", endDate = "2026-08-23"),
            range.withStartDate("2026-08-20", allDay = false),
        )
    }

    @Test
    fun `moving the start date of an all-day event keeps its length in days`() {
        assertEquals(
            allDay(startDate = "2026-09-01", endDate = "2026-09-03"),
            allDay(startDate = "2026-08-11", endDate = "2026-08-13")
                .withStartDate("2026-09-01", allDay = true),
        )
    }

    @Test
    fun `moving the start time keeps the duration`() {
        assertEquals(
            timed(startTime = "14:30", endTime = "15:30"),
            timed().withStartTime("14:30", allDay = false),
        )
    }

    @Test
    fun `moving the start time earlier keeps the duration`() {
        assertEquals(
            timed(startTime = "07:00", endTime = "08:00"),
            timed().withStartTime("07:00", allDay = false),
        )
    }

    @Test
    fun `a start time that pushes the end past midnight moves the end date`() {
        val range = timed(startTime = "20:00", endTime = "22:00")
        assertEquals(
            timed(startTime = "23:00", endDate = "2026-08-12", endTime = "01:00"),
            range.withStartTime("23:00", allDay = false),
        )
    }

    @Test
    fun `a start time change on an event that already ends the next day keeps the duration`() {
        val range = timed(startTime = "22:00", endDate = "2026-08-12", endTime = "01:00")
        assertEquals(
            timed(startTime = "10:00", endDate = "2026-08-11", endTime = "13:00"),
            range.withStartTime("10:00", allDay = false),
        )
    }

    // --- moving the start when the end is missing or already broken ----------

    @Test
    fun `a start time change fills in a missing end`() {
        val range = EventTimeRange(startDate = "2026-08-11", endDate = "2026-08-11")
        assertEquals(
            timed(startTime = "14:00", endTime = "15:00"),
            range.withStartTime("14:00", allDay = false),
        )
    }

    @Test
    fun `a start time change repairs an end that was already before the start`() {
        val range = timed(startTime = "10:00", endTime = "09:00")
        assertEquals(
            timed(startTime = "16:00", endTime = "17:00"),
            range.withStartTime("16:00", allDay = false),
        )
    }

    @Test
    fun `a start date change repairs an end date that was already before the start`() {
        val range = allDay(startDate = "2026-08-11", endDate = "2026-08-09")
        assertEquals(
            allDay(startDate = "2026-08-20", endDate = "2026-08-20"),
            range.withStartDate("2026-08-20", allDay = true),
        )
    }

    @Test
    fun `a start date change on a timed event with no times still moves the end date`() {
        val range = EventTimeRange(startDate = "2026-08-11", endDate = "2026-08-12")
        assertEquals(
            EventTimeRange(startDate = "2026-09-01", endDate = "2026-09-02"),
            range.withStartDate("2026-09-01", allDay = false),
        )
    }

    @Test
    fun `an unreadable start date leaves the end alone`() {
        assertEquals(
            timed(startDate = "not-a-date"),
            timed().withStartDate("not-a-date", allDay = false),
        )
    }

    // --- moving the end ------------------------------------------------------

    @Test
    fun `a later end is taken as given`() {
        assertEquals(
            timed(endTime = "17:00"),
            timed().withEndTime("17:00", allDay = false),
        )
    }

    @Test
    fun `an end time before the start moves the end to the next day`() {
        assertEquals(
            timed(endDate = "2026-08-12", endTime = "02:00"),
            timed().withEndTime("02:00", allDay = false),
        )
    }

    @Test
    fun `an end time equal to the start moves the end to the next day`() {
        assertEquals(
            timed(endDate = "2026-08-12", endTime = "10:00"),
            timed().withEndTime("10:00", allDay = false),
        )
    }

    @Test
    fun `an end date before the start date snaps up to the start date`() {
        assertEquals(
            timed(endDate = "2026-08-11", endTime = "11:00"),
            timed().withEndDate("2026-08-01", allDay = false),
        )
    }

    @Test
    fun `an end date before the start date snaps past it when the time alone is not enough`() {
        assertEquals(
            timed(endDate = "2026-08-12", endTime = "09:00"),
            timed(endTime = "09:00").withEndDate("2026-08-01", allDay = false),
        )
    }

    @Test
    fun `an all-day end date before the start date snaps up to the start date`() {
        assertEquals(
            allDay(startDate = "2026-08-11", endDate = "2026-08-11"),
            allDay().withEndDate("2026-08-01", allDay = true),
        )
    }

    @Test
    fun `an all-day event may end on the day it starts`() {
        assertEquals(
            allDay(startDate = "2026-08-11", endDate = "2026-08-11"),
            allDay(endDate = "2026-08-20").withEndDate("2026-08-11", allDay = true),
        )
    }

    // --- the all-day toggle --------------------------------------------------

    @Test
    fun `turning all-day off gives a single-day event times`() {
        assertEquals(
            timed(startTime = "09:00", endTime = "10:00"),
            allDay().withAllDay(false),
        )
    }

    @Test
    fun `turning all-day off keeps a multi-day event spanning the same days`() {
        assertEquals(
            timed(startTime = "09:00", endDate = "2026-08-13", endTime = "09:00"),
            allDay(endDate = "2026-08-13").withAllDay(false),
        )
    }

    @Test
    fun `turning all-day on keeps the times for turning it back off`() {
        assertEquals(timed(), timed().withAllDay(true))
    }

    @Test
    fun `turning all-day off leaves an event that already has times alone`() {
        assertEquals(timed(), timed().withAllDay(false))
    }

    @Test
    fun `turning all-day off repairs times the dates moved out from under`() {
        // The round trip that made this reachable: a multi-day all-day event becomes timed, goes
        // back to all-day, and is then shortened to a single day — leaving 09:00–09:00 behind.
        val range = allDay(endDate = "2026-08-13")
            .withAllDay(false)
            .withAllDay(true)
            .withEndDate("2026-08-11", allDay = true)
        assertEquals(timed(startTime = "09:00", endTime = "09:00"), range)
        assertEquals(timed(startTime = "09:00", endTime = "10:00"), range.withAllDay(false))
    }

    @Test
    fun `turning all-day off repairs an end that is before the start`() {
        // A leftover end time is not a choice worth keeping, so this takes the default length
        // rather than the next-day treatment an explicitly picked end time would get.
        assertEquals(
            timed(startTime = "10:00", endTime = "11:00"),
            timed(endTime = "09:00").withAllDay(false),
        )
    }

    @Test
    fun `an all-day event may be shortened to a single day`() {
        assertEquals(
            allDay(endDate = "2026-08-11"),
            allDay(endDate = "2026-08-13").withEndDate("2026-08-11", allDay = true),
        )
    }

    // --- daylight saving -----------------------------------------------------

    @Test
    fun `an event keeps its wall-clock length across a daylight saving transition`() {
        // The form deals in wall-clock times, so moving an event onto the day the clocks change
        // keeps the times it displays. Anything else would silently rewrite what the user typed.
        assertEquals(
            timed(startDate = "2026-03-29", endDate = "2026-03-29"),
            timed().withStartDate("2026-03-29", allDay = false),
        )
    }

    // --- normalizing a range from outside the form ---------------------------

    @Test
    fun `an inconsistent prefill is normalized`() {
        assertEquals(
            timed(startTime = "14:00", endDate = "2026-08-12", endTime = "09:00"),
            timed(startTime = "14:00", endTime = "09:00").normalized(allDay = false),
        )
    }

    @Test
    fun `a consistent prefill is left alone`() {
        assertEquals(timed(), timed().normalized(allDay = false))
    }

    @Test
    fun `an all-day range ending before it starts is normalized`() {
        assertEquals(
            allDay(startDate = "2026-08-11", endDate = "2026-08-11"),
            allDay(startDate = "2026-08-11", endDate = "2026-08-01").normalized(allDay = true),
        )
    }

    // --- the defaults a new event opens with ---------------------------------

    private val noon = LocalDateTime.parse("2026-08-11T12:20")

    @Test
    fun `a new event starts on the next full hour and lasts an hour`() {
        assertEquals(timed(startTime = "13:00", endTime = "14:00"), newEventRange(null, noon))
    }

    @Test
    fun `a new event created late in the evening starts tomorrow`() {
        // Deriving the date and the time separately would put this at 00:00 *today* — in the past.
        assertEquals(
            timed(startDate = "2026-08-12", startTime = "00:00", endDate = "2026-08-12", endTime = "01:00"),
            newEventRange(null, LocalDateTime.parse("2026-08-11T23:30")),
        )
    }

    @Test
    fun `a prefill wins over the defaults`() {
        val prefill = NewEventPrefill(
            startDate = "2026-09-01",
            startTime = "08:00",
            endDate = "2026-09-01",
            endTime = "09:30",
        )
        assertEquals(
            timed(startDate = "2026-09-01", startTime = "08:00", endDate = "2026-09-01", endTime = "09:30"),
            newEventRange(prefill, noon),
        )
    }

    @Test
    fun `a prefill with only a start date ends on that date`() {
        assertEquals(
            timed(startDate = "2026-09-01", startTime = "13:00", endDate = "2026-09-01", endTime = "14:00"),
            newEventRange(NewEventPrefill(startDate = "2026-09-01"), noon),
        )
    }

    @Test
    fun `an inconsistent prefill is normalized rather than trusted`() {
        val prefill = NewEventPrefill(
            startDate = "2026-09-01",
            startTime = "22:00",
            endDate = "2026-09-01",
            endTime = "02:00",
        )
        assertEquals(
            timed(startDate = "2026-09-01", startTime = "22:00", endDate = "2026-09-02", endTime = "02:00"),
            newEventRange(prefill, noon),
        )
    }

    @Test
    fun `an all-day prefill ending before it starts is normalized on its dates`() {
        val prefill = NewEventPrefill(startDate = "2026-09-01", endDate = "2026-08-01", allDay = true)
        val range = newEventRange(prefill, noon)
        assertEquals("2026-09-01", range.startDate)
        assertEquals("2026-09-01", range.endDate)
    }
}
