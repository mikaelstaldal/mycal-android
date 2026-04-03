package nu.staldal.mycal.data

import android.util.Log
import nu.staldal.mycal.data.api.ApiService
import nu.staldal.mycal.data.api.CalendarDto
import nu.staldal.mycal.data.api.CreateEventRequest
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.UpdateEventRequest
import nu.staldal.mycal.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

const val LOGTAG = "EventRepository"

class EventRepository(
    database: AppDatabase,
    private val apiProvider: () -> ApiService?,
) {
    private val eventDao = database.eventDao()
    private val pendingChangeDao = database.pendingChangeDao()
    private val calendarDao = database.calendarDao()

    fun getEventsBetween(from: String, to: String, hiddenCalendarIds: Set<Int> = emptySet()): Flow<List<EventDto>> {
        val flow = if (hiddenCalendarIds.isEmpty()) {
            eventDao.getEventsBetween(from, to)
        } else {
            eventDao.getEventsBetweenFiltered(from, to, hiddenCalendarIds.toList())
        }
        return flow.map { entities -> entities.map { it.toDto() } }
    }

    suspend fun searchEvents(query: String, hiddenCalendarIds: Set<Int> = emptySet()): List<EventDto> {
        val entities = if (hiddenCalendarIds.isEmpty()) {
            eventDao.searchEvents(query)
        } else {
            eventDao.searchEventsFiltered(query, hiddenCalendarIds.toList())
        }
        return entities.map { it.toDto() }
    }

    suspend fun getEvent(id: String): EventDto? {
        return eventDao.getEventById(id)?.toDto()
    }

    suspend fun refreshEvents(from: String, to: String) {
        val api = apiProvider() ?: return
        val response = api.listEvents(from, to)
        if (response.isSuccessful) {
            val events = response.body() ?: emptyList()
            val entities = events.map { it.toEntity() }
            eventDao.upsertEvents(entities)
            eventDao.deleteStaleEvents(
                keepIds = entities.map { it.id },
                from = from,
                to = to,
            )
        } else {
            throw Exception("Error: ${response.code()}")
        }
    }

    suspend fun createEvent(request: CreateEventRequest): String {
        val minId = eventDao.getMinTempId() ?: 0
        val tempId = if (minId >= 0) -1 else minId - 1

        val entity = EventEntity(
            id = tempId.toString(),
            title = request.title,
            description = request.description,
            startTime = request.startTime,
            endTime = request.endTime,
            allDay = request.allDay,
            color = request.color,
            location = request.location,
            url = request.url,
            reminderMinutes = request.reminderMinutes,
            recurrenceFreq = request.recurrenceFreq ?: "",
            recurrenceCount = request.recurrenceCount,
            recurrenceUntil = request.recurrenceUntil,
            recurrenceInterval = request.recurrenceInterval,
            recurrenceByDay = request.recurrenceByDay,
            recurrenceByMonthday = request.recurrenceByMonthday,
            recurrenceByMonth = request.recurrenceByMonth,
        )
        eventDao.upsertEvent(entity)
        pendingChangeDao.insert(PendingChange(eventId = tempId.toString(), changeType = ChangeType.CREATE))
        return tempId.toString()
    }

    suspend fun updateEvent(id: String, request: UpdateEventRequest) {
        val existing = eventDao.getEventById(id) ?: return
        val updated = existing.copy(
            title = request.title ?: existing.title,
            description = request.description ?: existing.description,
            startTime = request.startTime ?: existing.startTime,
            endTime = request.endTime ?: existing.endTime,
            allDay = request.allDay ?: existing.allDay,
            color = request.color ?: existing.color,
            location = request.location ?: existing.location,
            url = request.url ?: existing.url,
            reminderMinutes = request.reminderMinutes ?: existing.reminderMinutes,
            recurrenceFreq = request.recurrenceFreq ?: existing.recurrenceFreq,
            recurrenceCount = request.recurrenceCount ?: existing.recurrenceCount,
            recurrenceUntil = request.recurrenceUntil ?: existing.recurrenceUntil,
            recurrenceInterval = request.recurrenceInterval ?: existing.recurrenceInterval,
            recurrenceByDay = request.recurrenceByDay ?: existing.recurrenceByDay,
            recurrenceByMonthday = request.recurrenceByMonthday ?: existing.recurrenceByMonthday,
            recurrenceByMonth = request.recurrenceByMonth ?: existing.recurrenceByMonth,
        )
        eventDao.upsertEvent(updated)
        // If this is a local-only event (negative ID), the CREATE change will push the latest state
        if (!id.startsWith("-")) {
            pendingChangeDao.insert(PendingChange(eventId = id, changeType = ChangeType.UPDATE))
        }
    }

    suspend fun deleteEvent(id: String) {
        eventDao.deleteEvent(id)
        if (id.startsWith("-")) {
            // Never synced — just remove pending CREATE
            pendingChangeDao.deleteByEventId(id)
        } else {
            // Remove any pending non-delete changes, add DELETE
            pendingChangeDao.deleteNonDeleteByEventId(id)
            pendingChangeDao.insert(PendingChange(eventId = id, changeType = ChangeType.DELETE))
        }
    }

    suspend fun syncPendingChanges() {
        val api = apiProvider() ?: return
        val changes = pendingChangeDao.getAllChanges()

        Log.i(LOGTAG, "Syncing changes to backend")

        for (change in changes) {
            try {
                when (change.changeType) {
                    ChangeType.CREATE -> {
                        val entity = eventDao.getEventById(change.eventId)
                        if (entity != null) {
                            val response = api.createEvent(entity.toCreateRequest())
                            if (response.isSuccessful) {
                                val serverEvent = response.body()!!
                                eventDao.deleteEvent(change.eventId)
                                eventDao.upsertEvent(serverEvent.toEntity())
                                pendingChangeDao.delete(change)
                            } else {
                                Log.w(LOGTAG,"Unable to create event on backend: ${response.code()} ${response.message()}")
                            }
                        } else {
                            // Entity was deleted locally, remove the pending create operation
                            pendingChangeDao.delete(change)
                        }
                    }
                    ChangeType.UPDATE -> {
                        val entity = eventDao.getEventById(change.eventId)
                        if (entity != null) {
                            val response = api.updateEvent(change.eventId, entity.toUpdateRequest())
                            if (response.isSuccessful) {
                                val serverEvent = response.body()!!
                                eventDao.upsertEvent(serverEvent.toEntity())
                                pendingChangeDao.delete(change)
                            } else if (response.code() == 404) {
                                // Server-wins: event deleted on server
                                eventDao.deleteEvent(change.eventId)
                                pendingChangeDao.delete(change)
                            } else {
                                Log.w(LOGTAG,"Unable to update event on backend: ${response.code()} ${response.message()}")
                            }
                        } else {
                            pendingChangeDao.delete(change)
                        }
                    }
                    ChangeType.DELETE -> {
                        val response = api.deleteEvent(change.eventId)
                        if (response.isSuccessful || response.code() == 404) {
                            pendingChangeDao.delete(change)
                        } else {
                            Log.w(LOGTAG,"Unable to delete event on backend: ${response.code()} ${response.message()}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.i(LOGTAG, "Network error while syncing change to backend: $e")
                // Network error — stop processing will retry later
                return
            }
        }
    }

    fun getPendingChangeCount(): Flow<Int> = pendingChangeDao.getPendingCount()

    fun getCalendars(): Flow<List<CalendarDto>> {
        return calendarDao.getAll().map { entities ->
            entities.map { CalendarDto(id = it.id, name = it.name, color = it.color) }
        }
    }

    suspend fun refreshCalendars() {
        val api = apiProvider() ?: return
        val response = api.getCalendars()
        if (response.isSuccessful) {
            val calendars = response.body() ?: emptyList()
            calendarDao.upsertCalendars(calendars.map {
                nu.staldal.mycal.data.local.CalendarEntity(id = it.id, name = it.name, color = it.color)
            })
        }
    }
}
