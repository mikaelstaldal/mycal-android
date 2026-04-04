package nu.staldal.mycal.data.api

// UI-facing DTOs returned by EventRepository (built from local database).
// API response types (Event, Calendar, CreateEventRequest, UpdateEventRequest,
// UpdateCalendarRequest) are generated from openapi.yaml — do not edit them manually.

data class EventDto(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val allDay: Boolean = false,
    val color: String = "",
    val recurrenceFreq: String = "",
    val location: String = "",
    val categories: String = "",
    val url: String = "",
    val reminderMinutes: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val parentId: String? = null,
    val recurrenceCount: Int? = null,
    val recurrenceUntil: String? = null,
    val recurrenceInterval: Int? = null,
    val recurrenceByDay: String? = null,
    val recurrenceByMonthday: String? = null,
    val recurrenceByMonth: String? = null,
    val exdates: String? = null,
    val rdates: String? = null,
    val recurrenceParentId: String? = null,
    val recurrenceOriginalStart: String? = null,
    val duration: String? = null,
    val calendarId: Int = 0,
)

data class CalendarDto(
    val id: Int = 0,
    val name: String = "",
    val color: String = "",
)
