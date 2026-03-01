package nu.staldal.mycal.data.api

import com.google.gson.annotations.SerializedName

data class EventDto(
    val id: Long = 0,
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
)

data class CreateEventRequest(
    val title: String,
    val description: String = "",
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("all_day") val allDay: Boolean = false,
    val color: String = "",
    val location: String = "",
    @SerializedName("reminder_minutes") val reminderMinutes: Int = 0,
)

data class UpdateEventRequest(
    val title: String? = null,
    val description: String? = null,
    @SerializedName("start_time") val startTime: String? = null,
    @SerializedName("end_time") val endTime: String? = null,
    @SerializedName("all_day") val allDay: Boolean? = null,
    val color: String? = null,
    val location: String? = null,
    @SerializedName("reminder_minutes") val reminderMinutes: Int? = null,
)

data class ErrorResponse(
    val error: String,
)
