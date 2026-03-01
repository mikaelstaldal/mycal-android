package nu.staldal.mycal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey val id: Long,
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
)
