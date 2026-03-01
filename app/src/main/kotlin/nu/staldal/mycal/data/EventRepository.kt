package nu.staldal.mycal.data

import nu.staldal.mycal.data.api.ApiService
import nu.staldal.mycal.data.api.CreateEventRequest
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.UpdateEventRequest
import nu.staldal.mycal.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EventRepository(
    private val database: AppDatabase,
    private val apiProvider: () -> ApiService?,
) {
    private val eventDao = database.eventDao()
    private val pendingChangeDao = database.pendingChangeDao()

    fun getEventsBetween(from: String, to: String): Flow<List<EventDto>> {
        return eventDao.getEventsBetween(from, to).map { entities ->
            entities.map { it.toDto() }
        }
    }

    suspend fun searchEvents(query: String): List<EventDto> {
        return eventDao.searchEvents(query).map { it.toDto() }
    }

    suspend fun getEvent(id: Long): EventDto? {
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

    suspend fun createEvent(request: CreateEventRequest): Long {
        val minId = eventDao.getMinId() ?: 0
        val tempId = if (minId >= 0) -1 else minId - 1

        val entity = EventEntity(
            id = tempId,
            title = request.title,
            description = request.description,
            startTime = request.startTime,
            endTime = request.endTime,
            allDay = request.allDay,
            color = request.color,
            location = request.location,
        )
        eventDao.upsertEvent(entity)
        pendingChangeDao.insert(PendingChange(eventId = tempId, changeType = ChangeType.CREATE))
        return tempId
    }

    suspend fun updateEvent(id: Long, request: UpdateEventRequest) {
        val existing = eventDao.getEventById(id) ?: return
        val updated = existing.copy(
            title = request.title ?: existing.title,
            description = request.description ?: existing.description,
            startTime = request.startTime ?: existing.startTime,
            endTime = request.endTime ?: existing.endTime,
            allDay = request.allDay ?: existing.allDay,
            color = request.color ?: existing.color,
            location = request.location ?: existing.location,
        )
        eventDao.upsertEvent(updated)
        // If this is a local-only event (negative ID), the CREATE change will push latest state
        if (id > 0) {
            pendingChangeDao.insert(PendingChange(eventId = id, changeType = ChangeType.UPDATE))
        }
    }

    suspend fun deleteEvent(id: Long) {
        eventDao.deleteEvent(id)
        if (id < 0) {
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
                            }
                        } else {
                            // Entity was deleted locally, remove the pending create
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
                            }
                        } else {
                            pendingChangeDao.delete(change)
                        }
                    }
                    ChangeType.DELETE -> {
                        val response = api.deleteEvent(change.eventId)
                        if (response.isSuccessful || response.code() == 404) {
                            pendingChangeDao.delete(change)
                        }
                    }
                }
            } catch (_: Exception) {
                // Network error — stop processing, will retry later
                return
            }
        }
    }

    fun getPendingChangeCount(): Flow<Int> = pendingChangeDao.getPendingCount()
}
