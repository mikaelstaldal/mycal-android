package nu.staldal.mycal.data

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import java.io.IOException
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.data.api.CalendarDto
import nu.staldal.mycal.data.api.CreateEventRequest
import nu.staldal.mycal.data.api.DefaultApi
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.UpdateEventRequest
import nu.staldal.mycal.data.local.*
import nu.staldal.mycal.util.DateUtils
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val LOGTAG = "EventRepository"

private const val SERIES_NEEDS_SERVER = "Editing all events of a series needs a connection to the server"

class EventRepository(
    private val database: AppDatabase,
    private val context: Context,
    private val apiProvider: () -> DefaultApi?,
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

    /**
     * Fetches the recurring series [id] itself, for editing all of its events. It comes from the
     * server, never from the local database: that holds the series' expanded instances, each with
     * the series id as `parentId` and a composite id of its own. A synced row under the bare
     * series id is a copy of what an older server reported as the series' first instance, or of a
     * create response. It is not used, since its start may be stale (the server moves the start
     * when the first occurrence is deleted), and saving it back would restore occurrences deleted
     * since.
     */
    suspend fun fetchSeries(id: String): EventDto? {
        val api = apiProvider() ?: throw Exception(SERIES_NEEDS_SERVER)
        val response = seriesRequest { api.apiV1EventsIdGet(id = id) }
        return when {
            response.isSuccessful -> response.body()!!.toEntity().toDto()
            response.code() == 404 -> null
            else -> throw Exception("Unable to fetch event: ${response.code()} ${response.message()}")
        }
    }

    /**
     * Saves the recurring series [id] fetched by [fetchSeries]. Unlike [updateEvent] this goes
     * straight to the server, as there is no local row to queue the change against.
     *
     * Pending changes are pushed first, so that the refresh afterwards cannot overwrite an instance
     * edit or resurrect an instance deletion that has not reached the server yet. The series'
     * cached rows are then dropped, with their reminders, and their range is fetched again; deleting
     * them outright rather than through [EventDao.deleteStaleEvents] also catches all-day rows,
     * whose date-only times do not compare reliably with the RFC 3339 bounds of a refresh. If that
     * refresh fails, the save still stands and the old rows are put back.
     */
    suspend fun updateSeries(id: String, request: UpdateEventRequest) {
        val api = apiProvider() ?: throw Exception(SERIES_NEEDS_SERVER)
        seriesRequest { syncPendingChanges() }
        val response = seriesRequest { api.apiV1EventsIdPatch(id = id, updateEventRequest = request) }
        if (!response.isSuccessful) {
            throw Exception("Unable to update event on backend: ${response.code()} ${response.message()}")
        }
        val cached = eventDao.getEventAndInstances(id)
        if (cached.isEmpty()) return
        NotificationScheduler.withReminderLock {
            eventDao.deleteEventAndInstances(id)
            for (row in cached) NotificationScheduler.cancelNotification(context, row.id)
        }
        val from = cached.mapNotNull { DateUtils.parseToLocalDateTime(it.startTime) }.minOrNull() ?: return
        val to = cached.mapNotNull { DateUtils.parseToLocalDateTime(it.endTime) }.maxOrNull() ?: return
        try {
            refreshEvents(DateUtils.toRfc3339(from.minusDays(1)), DateUtils.toRfc3339(to.plusDays(1)))
        } catch (e: Exception) {
            // The series is saved; only the refresh failed. Show the old instances until the next
            // refresh rather than none.
            Log.w(LOGTAG, "Refresh after saving series $id failed: $e")
            eventDao.upsertEvents(cached)
            NotificationScheduler.rescheduleAllNotifications(context, database)
        }
    }

    /** Runs a request [fetchSeries] or [updateSeries] cannot do without, reporting a network failure plainly. */
    private suspend fun <T> seriesRequest(block: suspend () -> T): T =
        try {
            block()
        } catch (e: IOException) {
            Log.w(LOGTAG, "Series request failed: $e")
            throw Exception(SERIES_NEEDS_SERVER, e)
        }

    suspend fun refreshEvents(from: String, to: String) = syncMutex.withLock {
        reconcileReminders { refreshEventsFromServer(from, to) }
    }

    private suspend fun refreshEventsFromServer(from: String, to: String) {
        val api = apiProvider() ?: return
        val response = api.apiV1EventsGet(from = from, to = to)
        if (response.isSuccessful) {
            val events = response.body() ?: emptyList()
            database.withTransaction {
                // A reconnect refresh may run before WorkManager pushes offline deletions.
                val deletedIds = pendingChangeDao.getAllChanges()
                    .filter { it.changeType == ChangeType.DELETE }
                    .map { it.eventId }.toSet()
                val entities = events.map { it.toEntity() }
                    .filter { it.id !in deletedIds && it.parentId !in deletedIds }
                eventDao.upsertEvents(entities)
                eventDao.deleteStaleEvents(
                    keepIds = entities.map { it.id },
                    from = from,
                    to = to,
                )
                // A series is shown through its instances only. A row under the bare series id, left
                // by a create response or by an older server that reported the first instance under
                // it, would duplicate one of them.
                for (seriesId in entities.mapNotNull { it.parentId }.distinct()) {
                    eventDao.deleteEvent(seriesId)
                }
            }
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
            description = request.description ?: "",
            startTime = if (request.allDay) request.startDate ?: "" else request.startTime ?: "",
            endTime = if (request.allDay) request.endDate ?: "" else request.endTime ?: "",
            allDay = request.allDay,
            color = request.color ?: "",
            location = request.location ?: "",
            url = request.url ?: "",
            noteSlug = request.noteSlug ?: "",
            reminderMinutes = request.reminderMinutes ?: 0,
            latitude = request.latitude,
            longitude = request.longitude,
            recurrenceFreq = request.recurrenceFreq?.value ?: "",
            recurrenceCount = request.recurrenceCount,
            recurrenceUntil = request.recurrenceUntil,
            recurrenceInterval = request.recurrenceInterval,
            recurrenceByDay = request.recurrenceByDay,
            recurrenceByMonthday = request.recurrenceByMonthday,
            recurrenceByMonth = request.recurrenceByMonth,
            categories = request.categories ?: "",
            createdAt = "",
            updatedAt = "",
            parentId = null,
            exdates = null,
            rdates = null,
            recurrenceParentId = null,
            recurrenceOriginalStart = null,
            duration = null,
            calendarId = 0,
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
            allDay = request.allDay ?: existing.allDay,
            startTime = (if (request.allDay ?: existing.allDay) request.startDate else request.startTime) ?: existing.startTime,
            endTime = (if (request.allDay ?: existing.allDay) request.endDate else request.endTime) ?: existing.endTime,
            color = request.color ?: existing.color,
            location = request.location ?: existing.location,
            // Unlike most of the others, the empty string is meaningful here — it clears the URL.
            url = request.url ?: existing.url,
            // Likewise, the empty string here unlinks the note.
            noteSlug = request.noteSlug ?: existing.noteSlug,
            // And here it clears the categories.
            categories = request.categories ?: existing.categories,
            reminderMinutes = request.reminderMinutes ?: existing.reminderMinutes,
            recurrenceFreq = request.recurrenceFreq?.value ?: existing.recurrenceFreq,
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

    /**
     * Deletes the event [id] locally and queues its deletion on the server. Deleting a recurring
     * series also removes its cached instances, since none of them is stored under the series id.
     * Returns the ids of the rows removed.
     */
    suspend fun deleteEvent(id: String): List<String> = database.withTransaction {
        val removed = (listOf(id) + eventDao.getEventAndInstances(id).map { it.id }).distinct()
        eventDao.deleteEventAndInstances(id)
        if (id.startsWith("-")) {
            // Never synced — just remove pending CREATE
            pendingChangeDao.deleteByEventId(id)
        } else {
            // Remove any pending non-delete changes, add DELETE
            pendingChangeDao.deleteNonDeleteByEventId(id)
            pendingChangeDao.insert(PendingChange(eventId = id, changeType = ChangeType.DELETE))
        }
        removed
    }

    suspend fun syncPendingChanges() = syncMutex.withLock {
        reconcileReminders { syncChangesToServer() }
    }

    private suspend fun reconcileReminders(block: suspend () -> Unit) {
        val before = eventDao.getAllEvents()
        try {
            block()
        } finally {
            // A sync can apply some changes before a later request fails.
            withContext(NonCancellable) {
                NotificationScheduler.reconcileEvents(context, before, database)
            }
        }
    }

    private suspend fun syncChangesToServer() {
        val api = apiProvider() ?: return
        val changes = pendingChangeDao.getAllChanges()

        Log.i(LOGTAG, "Syncing changes to backend")

        for (change in changes) {
            try {
                when (change.changeType) {
                    ChangeType.CREATE -> {
                        val entity = eventDao.getEventById(change.eventId)
                        if (entity != null) {
                            val response = api.apiV1EventsPost(createEventRequest = entity.toCreateRequest())
                            if (response.isSuccessful) {
                                val serverEvent = response.body()!!
                                eventDao.deleteEvent(change.eventId)
                                eventDao.upsertEvent(serverEvent.toEntity())
                                pendingChangeDao.delete(change)
                            } else {
                                throw Exception("Unable to create event on backend: ${response.code()} ${response.message()}")
                            }
                        } else {
                            // Entity was deleted locally, remove the pending create operation
                            pendingChangeDao.delete(change)
                        }
                    }
                    ChangeType.UPDATE -> {
                        val entity = eventDao.getEventById(change.eventId)
                        if (entity != null) {
                            val response = api.apiV1EventsIdPatch(id = change.eventId, updateEventRequest = entity.toUpdateRequest())
                            if (response.isSuccessful) {
                                val serverEvent = response.body()!!
                                eventDao.upsertEvent(serverEvent.toEntity())
                                pendingChangeDao.delete(change)
                            } else if (response.code() == 404) {
                                // Server-wins: event deleted on server
                                eventDao.deleteEvent(change.eventId)
                                pendingChangeDao.delete(change)
                            } else {
                                throw Exception("Unable to update event on backend: ${response.code()} ${response.message()}")
                            }
                        } else {
                            pendingChangeDao.delete(change)
                        }
                    }
                    ChangeType.DELETE -> {
                        val response = api.apiV1EventsIdDelete(id = change.eventId)
                        if (response.isSuccessful || response.code() == 404) {
                            database.withTransaction {
                                eventDao.deleteEventAndInstances(change.eventId)
                                pendingChangeDao.delete(change)
                            }
                        } else {
                            throw Exception("Unable to delete event on backend: ${response.code()} ${response.message()}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(LOGTAG, "Failed to sync change to backend: $e")
                // Stop processing and propagate — WorkManager will retry, UI will show error
                throw e
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
        val response = api.apiV1CalendarsGet()
        if (response.isSuccessful) {
            val calendars = response.body() ?: emptyList()
            calendarDao.upsertCalendars(calendars.map {
                CalendarEntity(id = it.id.toInt(), name = it.name, color = it.color)
            })
        }
    }

    companion object {
        // EventRepository instances are created independently by ViewModels and WorkManager.
        // Serialize syncs and refreshes so server changes and reminder snapshots stay ordered.
        private val syncMutex = Mutex()
    }
}
