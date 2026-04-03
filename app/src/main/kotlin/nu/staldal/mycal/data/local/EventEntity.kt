package nu.staldal.mycal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val allDay: Boolean,
    val color: String,
    val recurrenceFreq: String,
    val location: String,
    val categories: String,
    val url: String,
    val reminderMinutes: Int,
    val latitude: Double?,
    val longitude: Double?,
    val createdAt: String,
    val updatedAt: String,
    val parentId: String?,
    val recurrenceCount: Int?,
    val recurrenceUntil: String?,
    val recurrenceInterval: Int?,
    val recurrenceByDay: String?,
    val recurrenceByMonthday: String?,
    val recurrenceByMonth: String?,
    val exdates: String?,
    val rdates: String?,
    val recurrenceParentId: String?,
    val recurrenceOriginalStart: String?,
    val duration: String?,
    val calendarId: Int,
)
