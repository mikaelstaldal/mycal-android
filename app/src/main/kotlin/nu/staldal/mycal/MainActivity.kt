package nu.staldal.mycal

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.ui.event.NewEventPrefill
import nu.staldal.mycal.ui.navigation.NavGraph
import nu.staldal.mycal.ui.theme.MyCalTheme
import nu.staldal.mycal.widget.ScheduleWidget
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        NotificationScheduler.createNotificationChannel(this)
        requestNotificationPermissionIfNeeded()

        val openSchedule = intent?.action == ScheduleWidget.ACTION_OPEN_SCHEDULE
        val openNewEvent = intent?.action == ScheduleWidget.ACTION_NEW_EVENT
        val viewEventId = if (intent?.action == ScheduleWidget.ACTION_VIEW_EVENT) {
            intent.getStringExtra(ScheduleWidget.EXTRA_EVENT_ID)
        } else null
        val newEventPrefill = intent?.let { parseInsertEventIntent(it) }

        setContent {
            MyCalTheme {
                NavGraph(
                    forceScheduleView = openSchedule,
                    openNewEvent = openNewEvent,
                    newEventPrefill = newEventPrefill,
                    viewEventId = viewEventId,
                )
            }
        }
    }

    /**
     * Parses the standard [Intent.ACTION_INSERT] calendar intent (as sent by e.g. other calendar
     * apps or assistants to create a new event), extracting the fields defined by
     * [CalendarContract.Events] and [CalendarContract.EXTRA_EVENT_BEGIN_TIME] /
     * [CalendarContract.EXTRA_EVENT_END_TIME] / [CalendarContract.EXTRA_EVENT_ALL_DAY].
     */
    private fun parseInsertEventIntent(intent: Intent): NewEventPrefill? {
        if (intent.action != Intent.ACTION_INSERT) return null

        val allDay = intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        // All-day events are conventionally expressed as UTC midnight; timed events use the local zone.
        fun dateOf(millis: Long) =
            if (allDay) Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
            else Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toString()

        // CalendarContract's all-day end is exclusive — the UTC midnight *after* the last day —
        // whereas the form's end date is the last day itself.
        fun endDateOf(millis: Long) =
            if (allDay) Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().minusDays(1).toString()
            else dateOf(millis)

        fun timeOf(millis: Long) =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(timeFormatter)

        val beginTime = intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L).takeIf { it >= 0 }
        val endTime = intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L).takeIf { it >= 0 }

        return NewEventPrefill(
            title = intent.getStringExtra(CalendarContract.Events.TITLE),
            description = intent.getStringExtra(CalendarContract.Events.DESCRIPTION),
            location = intent.getStringExtra(CalendarContract.Events.EVENT_LOCATION),
            startDate = beginTime?.let { dateOf(it) },
            startTime = if (allDay) null else beginTime?.let { timeOf(it) },
            endDate = endTime?.let { endDateOf(it) },
            endTime = if (allDay) null else endTime?.let { timeOf(it) },
            allDay = allDay,
        )
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
