package nu.staldal.mycal.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: Int,
    val name: String = "",
    val color: String = "",
)
