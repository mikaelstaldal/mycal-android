package nu.staldal.mycal

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.ui.navigation.NavGraph
import nu.staldal.mycal.ui.theme.MyCalTheme
import nu.staldal.mycal.widget.ScheduleWidget

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
            intent.getLongExtra(ScheduleWidget.EXTRA_EVENT_ID, -1L).takeIf { it >= 0 }
        } else null

        setContent {
            MyCalTheme {
                NavGraph(
                    forceScheduleView = openSchedule,
                    openNewEvent = openNewEvent,
                    viewEventId = viewEventId,
                )
            }
        }
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
