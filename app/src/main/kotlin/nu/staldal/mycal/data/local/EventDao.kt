package nu.staldal.mycal.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE (startTime >= :from AND startTime < :to) OR (startTime < :from AND endTime > :from) ORDER BY startTime")
    fun getEventsBetween(from: String, to: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE (startTime >= :from AND startTime < :to) OR (startTime < :from AND endTime > :from) ORDER BY startTime")
    fun getEventsBetweenBlocking(from: String, to: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE ((startTime >= :from AND startTime < :to) OR (startTime < :from AND endTime > :from)) AND calendarId NOT IN (:hiddenCalendarIds) ORDER BY startTime")
    fun getEventsBetweenFiltered(from: String, to: String, hiddenCalendarIds: List<Int>): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE ((startTime >= :from AND startTime < :to) OR (startTime < :from AND endTime > :from)) AND calendarId NOT IN (:hiddenCalendarIds) ORDER BY startTime")
    fun getEventsBetweenBlockingFiltered(from: String, to: String, hiddenCalendarIds: List<Int>): List<EventEntity>

    @Query("SELECT * FROM events WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%' ORDER BY startTime")
    suspend fun searchEvents(query: String): List<EventEntity>

    @Query("SELECT * FROM events WHERE (title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%') AND calendarId NOT IN (:hiddenCalendarIds) ORDER BY startTime")
    suspend fun searchEventsFiltered(query: String, hiddenCalendarIds: List<Int>): List<EventEntity>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getEventById(id: String): EventEntity?

    @Upsert
    suspend fun upsertEvents(events: List<EventEntity>)

    @Upsert
    suspend fun upsertEvent(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM events WHERE id NOT IN (:keepIds) AND id NOT LIKE '-%' AND startTime >= :from AND startTime < :to")
    suspend fun deleteStaleEvents(keepIds: List<String>, from: String, to: String)

    @Query("SELECT MIN(CAST(id AS INTEGER)) FROM events WHERE id GLOB '-*'")
    suspend fun getMinTempId(): Long?

    @Query("SELECT * FROM events WHERE reminderMinutes > 0 AND startTime >= :from AND startTime < :to")
    suspend fun getEventsWithReminders(from: String, to: String): List<EventEntity>
}
