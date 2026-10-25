package nu.staldal.mycal.data

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import nu.staldal.mycal.data.api.DefaultApi
import nu.staldal.mycal.data.api.Event
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.data.local.toEntity
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import retrofit2.Response
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class EventRepositoryTest {
    private val context = RuntimeEnvironment.getApplication()
    private val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    private val event = Event(
        id = "42", title = "Meeting",
        startTime = "2030-01-01T12:00:00Z", endTime = "2030-01-01T13:00:00Z",
    )
    private var serverEvents = listOf(event)
    private var deleteResponse: Response<Unit> = Response.success(Unit)
    private val api = Proxy.newProxyInstance(
        DefaultApi::class.java.classLoader, arrayOf(DefaultApi::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "apiV1EventsGet" -> Response.success(serverEvents)
            "apiV1EventsIdDelete" -> deleteResponse
            else -> error("Unexpected API call: ${method.name}")
        }
    } as DefaultApi
    private val repository = EventRepository(database, context) { api }

    @After fun closeDatabase() = database.close()

    @Test fun reconnectRefreshDoesNotRestoreOfflineDeletion() = runBlocking {
        database.eventDao().upsertEvent(event.toEntity())
        repository.deleteEvent(event.id)
        refresh()
        assertNull(repository.getEvent(event.id))
        assertEquals(1, database.pendingChangeDao().getAllChanges().size)
        repository.syncPendingChanges()
        assertNull(repository.getEvent(event.id))
        assertTrue(database.pendingChangeDao().getAllChanges().isEmpty())
    }

    @Test fun failedServerDeletionStaysHiddenOnRefresh() = runBlocking {
        database.eventDao().upsertEvent(event.toEntity())
        repository.deleteEvent(event.id)
        deleteResponse = Response.error(500, "".toResponseBody())
        try {
            repository.syncPendingChanges()
            fail("Expected server failure")
        } catch (_: Exception) {
            // The queued deletion must survive so WorkManager can retry.
        }
        refresh()
        assertNull(repository.getEvent(event.id))
        assertEquals(1, database.pendingChangeDao().getAllChanges().size)
    }

    @Test fun pendingSeriesDeletionHidesAllDayAndTimedInstances() = runBlocking {
        serverEvents = listOf(
            event.copy(id = "42_first", parentId = "42"),
            event.copy(id = "42_second", parentId = "42", allDay = true,
                startTime = null, endTime = null, startDate = "2030-01-02", endDate = "2030-01-03"),
            event.copy(id = "99"),
        )
        database.eventDao().upsertEvents(serverEvents.map { it.toEntity() })
        repository.deleteEvent("42")
        refresh()
        assertEquals(listOf("99"), database.eventDao().getAllEvents().map { it.id })
    }

    @Test fun confirmedDeletionRemovesPreviouslyRestoredCacheIncludingInstances() = runBlocking {
        for (alreadyDeleted in listOf(false, true)) {
            repository.deleteEvent("42")
            // Simulate stale rows left by a refresh from an earlier app version.
            database.eventDao().upsertEvents(listOf(
                event.toEntity(),
                event.copy(id = "42_first", parentId = "42").toEntity(),
            ))
            deleteResponse = if (alreadyDeleted) Response.error(404, "".toResponseBody())
                else Response.success(Unit)
            repository.syncPendingChanges()
            assertTrue(database.eventDao().getAllEvents().isEmpty())
            assertTrue(database.pendingChangeDao().getAllChanges().isEmpty())
        }
    }

    private suspend fun refresh() {
        repository.refreshEvents("2030-01-01T00:00:00Z", "2030-02-01T00:00:00Z")
    }
}
