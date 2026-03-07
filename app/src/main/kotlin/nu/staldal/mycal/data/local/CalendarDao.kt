package nu.staldal.mycal.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {
    @Query("SELECT * FROM calendars ORDER BY id")
    fun getAll(): Flow<List<CalendarEntity>>

    @Upsert
    suspend fun upsertCalendars(calendars: List<CalendarEntity>)
}
