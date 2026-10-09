package nu.staldal.mycal.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.data.local.EventEntity

object NotificationScheduler {
    private val reminderMutex = Mutex()

    internal suspend fun <T> withReminderLock(block: suspend () -> T): T =
        reminderMutex.withLock { block() }

    const val CHANNEL_ID = "mycal_event_reminders"
    private const val CHANNEL_NAME = "Event Reminders"

    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Notifications for upcoming calendar events"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun scheduleNotification(context: Context, eventId: String, title: String, triggerTimeMillis: Long) {
        if (triggerTimeMillis <= System.currentTimeMillis()) return

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("event_id", eventId)
            putExtra("event_title", title)
            putExtra("trigger_time", triggerTimeMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val alarmManager = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTimeMillis,
                pendingIntent,
            )
        }
    }

    fun cancelNotification(context: Context, eventId: String) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(pendingIntent)
        // An alarm may already have fired and left a notification in the system tray.
        context.getSystemService(NotificationManager::class.java).cancel(eventId.hashCode())
    }

    suspend fun reconcileEvents(context: Context, before: List<EventEntity>, database: AppDatabase) = withReminderLock {
        // Read current state under the same lock local saves and receiver validation use.
        val after = database.eventDao().getAllEvents()
        val changes = reminderChanges(
            before.associate { it.id to it.reminder() },
            after.associate { it.id to it.reminder() },
        )
        for (id in changes.cancelIds) cancelNotification(context, id)
        for ((id, reminder) in changes.schedule) {
            scheduleNotification(context, id, reminder.title, reminder.triggerMillis)
        }
    }

    suspend fun rescheduleAllNotifications(context: Context, database: AppDatabase) = withReminderLock {
        for (event in database.eventDao().getAllEvents()) {
            val reminder = event.reminder()
            if (reminder == null) {
                cancelNotification(context, event.id)
            } else {
                scheduleNotification(context, event.id, reminder.title, reminder.triggerMillis)
            }
        }
    }

    fun formatReminderMinutes(minutes: Int): String = when (minutes) {
        0 -> "None"
        5 -> "5 minutes before"
        10 -> "10 minutes before"
        15 -> "15 minutes before"
        30 -> "30 minutes before"
        60 -> "1 hour before"
        120 -> "2 hours before"
        1440 -> "1 day before"
        else -> "$minutes minutes before"
    }
}
