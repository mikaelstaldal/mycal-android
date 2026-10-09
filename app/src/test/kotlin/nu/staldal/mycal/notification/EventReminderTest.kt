package nu.staldal.mycal.notification

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class EventReminderTest {
    private val reminder = EventReminder.from("Meeting", "2030-01-01T12:00:00Z", 15)!!

    @Test fun deletesAndDisabledRemindersAreCancelled() {
        val changes = reminderChanges(mapOf("deleted" to reminder, "disabled" to reminder), mapOf("disabled" to null))
        assertEquals(setOf("deleted", "disabled"), changes.cancelIds)
        assertTrue(changes.schedule.isEmpty())
    }

    @Test fun reschedulesAndTitleChangesReplaceOldReminders() {
        for (updated in listOf(reminder.copy(triggerMillis = reminder.triggerMillis + 60_000), reminder.copy(title = "Renamed"))) {
            val changes = reminderChanges(mapOf("event" to reminder), mapOf("event" to updated))
            assertEquals(setOf("event"), changes.cancelIds)
            assertEquals(mapOf("event" to updated), changes.schedule)
        }
    }

    @Test fun unchangedRemindersArePreservedAndNewRemindersScheduled() {
        val changes = reminderChanges(mapOf("existing" to reminder), mapOf("existing" to reminder, "new" to reminder))
        assertTrue(changes.cancelIds.isEmpty())
        assertEquals(mapOf("new" to reminder), changes.schedule)
    }

    @Test fun temporaryIdReplacementRetiresOldReminder() {
        val changes = reminderChanges(mapOf("-1" to reminder), mapOf("42" to reminder))
        assertEquals(setOf("-1"), changes.cancelIds)
        assertEquals(mapOf("42" to reminder), changes.schedule)
    }

    @Test fun alarmsMustMatchCurrentReminderAndArriveBeforeEventStarts() {
        assertTrue(reminder.acceptsAlarm(reminder.triggerMillis, reminder.triggerMillis))
        assertTrue(reminder.acceptsAlarm(reminder.triggerMillis, reminder.triggerMillis + 1000))
        assertFalse(reminder.acceptsAlarm(reminder.triggerMillis - 60_000, reminder.triggerMillis))
        assertFalse(reminder.acceptsAlarm(Long.MIN_VALUE, reminder.triggerMillis))
        assertFalse(reminder.acceptsAlarm(reminder.triggerMillis, reminder.triggerMillis - 1))
        assertFalse(reminder.acceptsAlarm(reminder.triggerMillis, reminder.startMillis))
    }

    @Test fun changedStartOrLeadTimeRejectsPreviouslyScheduledAlarm() {
        val moved = EventReminder.from("Meeting", "2030-01-01T13:00:00Z", 15)!!
        val earlierReminder = EventReminder.from("Meeting", "2030-01-01T12:00:00Z", 30)!!
        assertEquals(15 * 60_000L, reminder.startMillis - reminder.triggerMillis)
        assertFalse(moved.acceptsAlarm(reminder.triggerMillis, reminder.triggerMillis))
        assertFalse(earlierReminder.acceptsAlarm(reminder.triggerMillis, reminder.triggerMillis))
    }

    @Test fun allDayReminderRecomputedAfterTimeZoneChangeUsesNewMidnight() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val utc = EventReminder.from("Holiday", "2030-01-01", 15)!!
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Stockholm"))
            val stockholm = EventReminder.from("Holiday", "2030-01-01", 15)!!
            assertEquals(60 * 60_000L, utc.triggerMillis - stockholm.triggerMillis)
            assertTrue(stockholm.acceptsAlarm(stockholm.triggerMillis, stockholm.triggerMillis))
            assertFalse(stockholm.acceptsAlarm(utc.triggerMillis, utc.triggerMillis))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test fun disabledOrMalformedRemindersCannotPost() {
        assertNull(EventReminder.from("Meeting", "2030-01-01T12:00:00Z", 0))
        assertNull(EventReminder.from("Meeting", "2030-01-01T12:00:00Z", -1))
        assertNull(EventReminder.from("Meeting", "invalid", 15))
    }
}
