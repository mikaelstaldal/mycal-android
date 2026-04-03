package nu.staldal.mycal.data.api

import com.google.gson.annotations.SerializedName

data class EventDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    @SerializedName("start_time") val startTime: String = "",
    @SerializedName("end_time") val endTime: String = "",
    @SerializedName("all_day") val allDay: Boolean = false,
    val color: String = "",
    @SerializedName("recurrence_freq") val recurrenceFreq: String = "",
    val location: String = "",
    val categories: String = "",
    val url: String = "",
    @SerializedName("reminder_minutes") val reminderMinutes: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("updated_at") val updatedAt: String = "",
    @SerializedName("parent_id") val parentId: String? = null,
    @SerializedName("recurrence_count") val recurrenceCount: Int? = null,
    @SerializedName("recurrence_until") val recurrenceUntil: String? = null,
    @SerializedName("recurrence_interval") val recurrenceInterval: Int? = null,
    @SerializedName("recurrence_byday") val recurrenceByDay: String? = null,
    @SerializedName("recurrence_bymonthday") val recurrenceByMonthday: String? = null,
    @SerializedName("recurrence_bymonth") val recurrenceByMonth: String? = null,
    val exdates: String? = null,
    val rdates: String? = null,
    @SerializedName("recurrence_parent_id") val recurrenceParentId: String? = null,
    @SerializedName("recurrence_original_start") val recurrenceOriginalStart: String? = null,
    val duration: String? = null,
    @SerializedName("calendar_id") val calendarId: Int = 0,
)

data class CreateEventRequest(
    val title: String,
    val description: String,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("all_day") val allDay: Boolean,
    val color: String,
    val location: String,
    val url: String,
    @SerializedName("reminder_minutes") val reminderMinutes: Int,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("recurrence_freq") val recurrenceFreq: String?,
    @SerializedName("recurrence_count") val recurrenceCount: Int?,
    @SerializedName("recurrence_until") val recurrenceUntil: String?,
    @SerializedName("recurrence_interval") val recurrenceInterval: Int?,
    @SerializedName("recurrence_byday") val recurrenceByDay: String?,
    @SerializedName("recurrence_bymonthday") val recurrenceByMonthday: String?,
    @SerializedName("recurrence_bymonth") val recurrenceByMonth: String?,
)

data class UpdateEventRequest(
    val title: String?,
    val description: String?,
    @SerializedName("start_time") val startTime: String?,
    @SerializedName("end_time") val endTime: String?,
    @SerializedName("all_day") val allDay: Boolean?,
    val color: String?,
    val location: String?,
    val url: String?,
    @SerializedName("reminder_minutes") val reminderMinutes: Int?,
    val latitude: Double?,
    val longitude: Double?,
    @SerializedName("recurrence_freq") val recurrenceFreq: String?,
    @SerializedName("recurrence_count") val recurrenceCount: Int?,
    @SerializedName("recurrence_until") val recurrenceUntil: String?,
    @SerializedName("recurrence_interval") val recurrenceInterval: Int?,
    @SerializedName("recurrence_byday") val recurrenceByDay: String?,
    @SerializedName("recurrence_bymonthday") val recurrenceByMonthday: String?,
    @SerializedName("recurrence_bymonth") val recurrenceByMonth: String?,
)

data class ErrorResponse(
    val error: String,
)

data class CalendarDto(
    val id: Int = 0,
    val name: String = "",
    val color: String = "",
)

data class UpdateCalendarRequest(
    val color: String? = null,
)
