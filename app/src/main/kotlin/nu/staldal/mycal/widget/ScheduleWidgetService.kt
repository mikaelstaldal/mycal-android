package nu.staldal.mycal.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.view.View
import nu.staldal.mycal.R
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.data.local.EventEntity
import nu.staldal.mycal.util.DateUtils
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return ScheduleWidgetFactory(applicationContext)
    }
}

private class ScheduleWidgetFactory(
    private val context: Context,
) : RemoteViewsService.RemoteViewsFactory {

    private var events: List<EventEntity> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        val dao = AppDatabase.getInstance(context).eventDao()
        val now = LocalDateTime.now()
        val from = DateUtils.toRfc3339(now.toLocalDate().atStartOfDay())
        val to = DateUtils.toRfc3339(now.toLocalDate().plusDays(14).atStartOfDay())

        events = dao.getEventsBetweenBlocking(from, to)
    }

    override fun getCount(): Int = events.size

    override fun getViewAt(position: Int): RemoteViews {
        val event = events[position]
        val views = RemoteViews(context.packageName, R.layout.widget_schedule_item)

        // Date column — show only for the first event of each day
        val eventDate = DateUtils.parseToLocalDate(event.startTime)
        val prevDate = if (position > 0) DateUtils.parseToLocalDate(events[position - 1].startTime) else null
        val showDate = eventDate != prevDate

        // Show extra space between days (not before the very first item)
        if (showDate && position > 0) {
            views.setViewVisibility(R.id.item_day_spacer, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.item_day_spacer, View.GONE)
        }

        if (showDate && eventDate != null) {
            val isToday = eventDate == LocalDate.now()
            views.setViewVisibility(R.id.item_date_column, View.VISIBLE)
            views.setViewVisibility(R.id.item_date_spacer, View.GONE)
            views.setTextViewText(
                R.id.item_day_of_week,
                eventDate.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            )
            if (isToday) {
                views.setViewVisibility(R.id.item_today_circle, View.VISIBLE)
                views.setViewVisibility(R.id.item_day_number, View.GONE)
                views.setTextViewText(R.id.item_day_number_today, eventDate.dayOfMonth.toString())
            } else {
                views.setViewVisibility(R.id.item_today_circle, View.GONE)
                views.setViewVisibility(R.id.item_day_number, View.VISIBLE)
                views.setTextViewText(R.id.item_day_number, eventDate.dayOfMonth.toString())
            }
        } else {
            views.setViewVisibility(R.id.item_date_column, View.GONE)
            views.setViewVisibility(R.id.item_date_spacer, View.VISIBLE)
        }

        // Event title
        views.setTextViewText(R.id.item_title, event.title)

        // Time text
        if (event.allDay) {
            views.setViewVisibility(R.id.item_time, View.GONE)
        } else {
            views.setViewVisibility(R.id.item_time, View.VISIBLE)
            val startStr = DateUtils.formatDisplayTime(event.startTime)
            val endStr = DateUtils.formatDisplayTime(event.endTime)
            views.setTextViewText(R.id.item_time, "$startStr - $endStr")
        }

        // Event color
        val color = cssColorToAndroidColor(event.color)
        views.setInt(R.id.item_background, "setColorFilter", color)

        // Set fill-in intent with event ID for item clicks
        val fillInIntent = Intent().apply {
            putExtra(ScheduleWidget.EXTRA_EVENT_ID, event.id)
        }
        views.setOnClickFillInIntent(R.id.item_root, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = events.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() {}
}

fun cssColorToAndroidColor(name: String?): Int {
    return when (name?.lowercase()) {
        "dodgerblue" -> Color.parseColor("#1E90FF")
        "red" -> Color.parseColor("#FF0000")
        "gold" -> Color.parseColor("#FFD700")
        "green" -> Color.parseColor("#008000")
        "orange" -> Color.parseColor("#FFA500")
        "mediumturquoise" -> Color.parseColor("#48D1CC")
        "cornflowerblue" -> Color.parseColor("#6495ED")
        "salmon" -> Color.parseColor("#FA8072")
        else -> Color.parseColor("#1E90FF") // default to dodgerblue
    }
}
