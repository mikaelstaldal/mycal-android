package nu.staldal.mycal.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.util.DateUtils
import java.time.ZoneId

object NotificationScheduler {
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
    }

    suspend fun rescheduleAllNotifications(context: Context, database: AppDatabase) {
        val now = java.time.LocalDateTime.now()
        val fromStr = DateUtils.toRfc3339(now)
        val toStr = DateUtils.toRfc3339(now.plusYears(1))
        val dao = database.eventDao()
        val events = dao.getEventsWithReminders(fromStr, toStr)
        for (event in events) {
            val ldt = DateUtils.parseToLocalDateTime(event.startTime) ?: continue
            val triggerMillis = ldt.minusMinutes(event.reminderMinutes.toLong())
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            scheduleNotification(context, event.id, event.title, triggerMillis)
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
