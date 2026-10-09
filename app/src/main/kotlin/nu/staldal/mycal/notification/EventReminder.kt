package nu.staldal.mycal.notification

import nu.staldal.mycal.data.local.EventEntity
import nu.staldal.mycal.util.DateUtils
import java.time.ZoneId

/** Values that identify an alarm and its displayed content. */
internal data class EventReminder(val title: String, val startMillis: Long, val triggerMillis: Long) {
    fun acceptsAlarm(trigger: Long, now: Long): Boolean =
        trigger == triggerMillis && trigger <= now && now < startMillis

    companion object {
        fun from(title: String, startTime: String, minutes: Int): EventReminder? {
            if (minutes <= 0) return null
            val start = DateUtils.parseToLocalDateTime(startTime) ?: return null
            val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            return EventReminder(title, startMillis, startMillis - minutes.toLong() * 60_000)
        }
    }
}

internal fun EventEntity.reminder(): EventReminder? =
    EventReminder.from(title, startTime, reminderMinutes)

internal data class ReminderChanges(
    val cancelIds: Set<String>,
    val schedule: Map<String, EventReminder>,
)

internal fun reminderChanges(
    before: Map<String, EventReminder?>,
    after: Map<String, EventReminder?>,
): ReminderChanges = ReminderChanges(
    cancelIds = before.keys.filterTo(mutableSetOf()) { before[it] != after[it] },
    schedule = after.mapNotNull { (id, reminder) ->
        if (reminder != null && reminder != before[id]) id to reminder else null
    }.toMap(),
)
