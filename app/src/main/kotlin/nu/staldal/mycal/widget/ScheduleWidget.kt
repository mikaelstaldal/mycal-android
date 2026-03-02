package nu.staldal.mycal.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import nu.staldal.mycal.MainActivity
import nu.staldal.mycal.R

class ScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_OPEN_SCHEDULE = "nu.staldal.mycal.OPEN_SCHEDULE"
        const val ACTION_NEW_EVENT = "nu.staldal.mycal.NEW_EVENT"
        const val ACTION_VIEW_EVENT = "nu.staldal.mycal.VIEW_EVENT"
        const val EXTRA_EVENT_ID = "nu.staldal.mycal.EVENT_ID"

        @Suppress("DEPRECATION")
        fun notifyDataChanged(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ScheduleWidget::class.java)
            )
            appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_list)
        }

        @Suppress("DEPRECATION")
        private fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_schedule)

            // Set up the RemoteViews adapter for the list
            val serviceIntent = Intent(context, ScheduleWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // Click on header title -> open app in schedule view
            val openScheduleIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_SCHEDULE
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openSchedulePi = PendingIntent.getActivity(
                context, 0, openScheduleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openSchedulePi)

            // Click on "+" button -> open app to new event
            val newEventIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_EVENT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newEventPi = PendingIntent.getActivity(
                context, 1, newEventIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_add_button, newEventPi)

            // Click on list items -> open event detail view
            val itemClickIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_VIEW_EVENT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val itemClickPi = PendingIntent.getActivity(
                context, 2, itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, itemClickPi)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
