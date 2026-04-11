package nu.staldal.mycal.ui.event

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import nu.staldal.mycal.MyCalApplication
import nu.staldal.mycal.data.EventRepository
import nu.staldal.mycal.data.api.CreateEventRequest
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.NominatimClient
import nu.staldal.mycal.data.api.NominatimPlace
import nu.staldal.mycal.data.api.RetrofitClient
import nu.staldal.mycal.data.api.UpdateEventRequest
import nu.staldal.mycal.data.preferences.UserPreferences
import nu.staldal.mycal.data.sync.SyncWorker
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.widget.ScheduleWidget
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

data class EventDetailState(
    val event: EventDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDeleted: Boolean = false,
    val defaultEventColor: String = "dodgerblue",
)

data class EventFormState(
    val title: String = "",
    val description: String = "",
    val url: String = "",
    val location: String = "",
    val startDate: String = "", // yyyy-MM-dd
    val startTime: String = "", // HH:mm
    val endDate: String = "",   // yyyy-MM-dd
    val endTime: String = "",   // HH:mm
    val allDay: Boolean = false,
    val color: String = "",
    val reminderMinutes: Int = 0,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
    // Recurrence fields
    val recurrenceFreq: String = "",
    val recurrenceInterval: Int = 1,
    val recurrenceCount: Int? = null,
    val recurrenceUntil: String? = null,
    val recurrenceByDay: String? = null,
    val recurrenceByMonthday: String? = null,
    val recurrenceByMonth: String? = null,
    // Recurring instance info
    val parentId: String? = null,
    val isRecurringInstance: Boolean = false,
)

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val database = (application as MyCalApplication).database
    private val _detailState = MutableStateFlow(EventDetailState())
    val detailState: StateFlow<EventDetailState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(EventFormState())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()

    private val _locationSuggestions = MutableStateFlow<List<NominatimPlace>>(emptyList())
    val locationSuggestions: StateFlow<List<NominatimPlace>> = _locationSuggestions.asStateFlow()

    private val _locationError = MutableStateFlow<String?>(null)
    val locationError: StateFlow<String?> = _locationError.asStateFlow()

    private val _locationQuery = MutableStateFlow("")

    init {
        viewModelScope.launch {
            prefs.defaultEventColor.collect { color ->
                _detailState.update { it.copy(defaultEventColor = color) }
            }
        }

        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _locationQuery
                .drop(1) // skip initial empty value
                .debounce(300.milliseconds)
                .collectLatest { query ->
                    if (query.length >= 3 && isNetworkAvailable()) {
                        try {
                            val results = NominatimClient.service.search(query)
                            _locationSuggestions.value = results
                            _locationError.value = null
                        } catch (e: Exception) {
                            android.util.Log.e("EventViewModel", "Nominatim search failed", e)
                            _locationSuggestions.value = emptyList()
                            _locationError.value = "Location search failed: ${e.message}"
                        }
                    } else {
                        _locationSuggestions.value = emptyList()
                        _locationError.value = null
                    }
                }
        }
    }

    private val serverConfigDeferred = viewModelScope.async {
        prefs.serverConfig.first()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val repository by lazy {
        EventRepository(database) {
            val config = serverConfigDeferred.getCompleted()
            RetrofitClient.getApiService(config.baseUrl, config.username, config.password)
        }
    }

    private suspend fun getRepository(): EventRepository {
        serverConfigDeferred.await()
        return repository
    }

    fun loadEvent(id: String) {
        viewModelScope.launch {
            val repo = getRepository()
            _detailState.update { it.copy(isLoading = true, error = null) }
            try {
                val event = repo.getEvent(id)
                if (event != null) {
                    _detailState.update { it.copy(event = event, isLoading = false) }
                } else {
                    _detailState.update { it.copy(isLoading = false, error = "Event not found") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteEvent(id: String) {
        viewModelScope.launch {
            val repo = getRepository()
            _detailState.update { it.copy(isLoading = true) }
            try {
                repo.deleteEvent(id)
                NotificationScheduler.cancelNotification(getApplication(), id)
                _detailState.update { it.copy(isDeleted = true, isLoading = false) }
                ScheduleWidget.notifyDataChanged(getApplication())
                SyncWorker.enqueueOneTime(getApplication())
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadEventForEdit(id: String) {
        viewModelScope.launch {
            val repo = getRepository()
            _formState.update { it.copy(isLoading = true) }
            try {
                val event = repo.getEvent(id)
                if (event != null) {
                    val startLdt = nu.staldal.mycal.util.DateUtils.parseToLocalDateTime(event.startTime)
                    val endLdt = nu.staldal.mycal.util.DateUtils.parseToLocalDateTime(event.endTime)
                    _formState.update {
                        it.copy(
                            title = event.title,
                            description = event.description,
                            url = event.url,
                            location = event.location,
                            startDate = startLdt?.toLocalDate()?.toString() ?: "",
                            startTime = if (event.allDay) "" else startLdt?.toLocalTime()?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                            endDate = endLdt?.toLocalDate()?.toString() ?: "",
                            endTime = if (event.allDay) "" else endLdt?.toLocalTime()?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                            allDay = event.allDay,
                            color = event.color,
                            reminderMinutes = event.reminderMinutes,
                            latitude = event.latitude,
                            longitude = event.longitude,
                            isLoading = false,
                            recurrenceFreq = event.recurrenceFreq,
                            recurrenceInterval = event.recurrenceInterval ?: 1,
                            recurrenceCount = event.recurrenceCount,
                            recurrenceUntil = event.recurrenceUntil,
                            recurrenceByDay = event.recurrenceByDay,
                            recurrenceByMonthday = event.recurrenceByMonthday,
                            recurrenceByMonth = event.recurrenceByMonth,
                            parentId = event.parentId,
                            isRecurringInstance = event.parentId != null,
                        )
                    }
                } else {
                    _formState.update { it.copy(isLoading = false, error = "Event not found") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateTitle(value: String) { _formState.update { it.copy(title = value) } }
    fun updateDescription(value: String) { _formState.update { it.copy(description = value) } }
    fun updateUrl(value: String) { _formState.update { it.copy(url = value) } }
    fun updateLocation(value: String) {
        _formState.update { it.copy(location = value, latitude = null, longitude = null) }
        _locationQuery.value = value
    }
    fun selectLocationSuggestion(place: NominatimPlace) {
        _formState.update {
            it.copy(
                location = place.display_name,
                latitude = place.lat.toDoubleOrNull(),
                longitude = place.lon.toDoubleOrNull(),
            )
        }
        _locationSuggestions.value = emptyList()
    }
    fun clearLocationSuggestions() { _locationSuggestions.value = emptyList() }
    fun updateStartDate(value: String) {
        _formState.update {
            if (it.startDate.isNotEmpty() && it.endDate.isNotEmpty()) {
                val oldStart = java.time.LocalDate.parse(it.startDate)
                val oldEnd = java.time.LocalDate.parse(it.endDate)
                val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(oldStart, oldEnd)
                val newEnd = java.time.LocalDate.parse(value).plusDays(daysDiff)
                it.copy(startDate = value, endDate = newEnd.toString())
            } else {
                it.copy(startDate = value)
            }
        }
    }
    fun updateStartTime(value: String) {
        _formState.update {
            if (it.startDate.isNotEmpty() && it.startTime.isNotEmpty() &&
                it.endDate.isNotEmpty() && it.endTime.isNotEmpty()) {
                val oldStart = java.time.LocalDateTime.parse("${it.startDate}T${it.startTime}")
                val oldEnd = java.time.LocalDateTime.parse("${it.endDate}T${it.endTime}")
                val duration = java.time.Duration.between(oldStart, oldEnd)
                val newEnd = java.time.LocalDateTime.parse("${it.startDate}T${value}").plus(duration)
                val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                it.copy(startTime = value, endDate = newEnd.toLocalDate().toString(), endTime = newEnd.toLocalTime().format(fmt))
            } else {
                it.copy(startTime = value)
            }
        }
    }
    fun updateEndDate(value: String) { _formState.update { it.copy(endDate = value) } }
    fun updateEndTime(value: String) { _formState.update { it.copy(endTime = value) } }
    fun updateAllDay(value: Boolean) { _formState.update { it.copy(allDay = value) } }
    fun updateColor(value: String) { _formState.update { it.copy(color = value) } }
    fun updateReminderMinutes(value: Int) { _formState.update { it.copy(reminderMinutes = value) } }
    fun updateRecurrenceFreq(value: String) {
        _formState.update {
            if (value.isBlank()) {
                it.copy(recurrenceFreq = "", recurrenceInterval = 1, recurrenceCount = null, recurrenceUntil = null, recurrenceByDay = null)
            } else {
                it.copy(recurrenceFreq = value)
            }
        }
    }
    fun updateRecurrenceInterval(value: Int) { _formState.update { it.copy(recurrenceInterval = value) } }
    fun updateRecurrenceCount(value: Int?) { _formState.update { it.copy(recurrenceCount = value, recurrenceUntil = null) } }
    fun updateRecurrenceUntil(value: String?) { _formState.update { it.copy(recurrenceUntil = value, recurrenceCount = null) } }
    fun updateRecurrenceByDay(value: String?) { _formState.update { it.copy(recurrenceByDay = value) } }

    fun createEvent() {
        val form = _formState.value

        val (startTimeStr, endTimeStr) = buildTimestamps(form) ?: return

        viewModelScope.launch {
            val repo = getRepository()
            _formState.update { it.copy(isSaving = true, error = null) }
            try {
                val request = CreateEventRequest(
                    title = form.title,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    description = form.description,
                    url = form.url.takeIf { it.isNotBlank() }?.let { java.net.URI(it) },
                    location = form.location,
                    allDay = form.allDay,
                    color = form.color,
                    reminderMinutes = form.reminderMinutes,
                    latitude = form.latitude,
                    longitude = form.longitude,
                    recurrenceFreq = form.recurrenceFreq.ifBlank { null }?.let { freq ->
                        CreateEventRequest.RecurrenceFreq.entries.firstOrNull { it.value == freq }
                    },
                    recurrenceCount = form.recurrenceCount,
                    recurrenceUntil = form.recurrenceUntil,
                    recurrenceInterval = if (form.recurrenceFreq.isNotBlank() && form.recurrenceInterval > 1) form.recurrenceInterval else null,
                    recurrenceByDay = form.recurrenceByDay,
                    recurrenceByMonthday = form.recurrenceByMonthday,
                    recurrenceByMonth = form.recurrenceByMonth,
                )
                val eventId = repo.createEvent(request)
                scheduleReminderIfNeeded(eventId, form.title, startTimeStr, form.reminderMinutes)
                _formState.update { it.copy(isSaving = false, isSaved = true) }
                ScheduleWidget.notifyDataChanged(getApplication())
                SyncWorker.enqueueOneTime(getApplication())
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateEvent(id: String) {
        val form = _formState.value

        val (startTimeStr, endTimeStr) = buildTimestamps(form) ?: return

        viewModelScope.launch {
            val repo = getRepository()
            _formState.update { it.copy(isSaving = true, error = null) }
            try {
                val request = UpdateEventRequest(
                    title = form.title,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    description = form.description,
                    url = form.url.takeIf { it.isNotBlank() }?.let { java.net.URI(it) },
                    location = form.location,
                    allDay = form.allDay,
                    color = form.color,
                    reminderMinutes = form.reminderMinutes,
                    latitude = form.latitude,
                    longitude = form.longitude,
                    recurrenceFreq = if (!form.isRecurringInstance) form.recurrenceFreq.ifBlank { null }?.let { freq ->
                        UpdateEventRequest.RecurrenceFreq.entries.firstOrNull { it.value == freq }
                    } else null,
                    recurrenceCount = if (!form.isRecurringInstance) form.recurrenceCount else null,
                    recurrenceUntil = if (!form.isRecurringInstance) form.recurrenceUntil else null,
                    recurrenceInterval = if (!form.isRecurringInstance && form.recurrenceFreq.isNotBlank() && form.recurrenceInterval > 1) form.recurrenceInterval else null,
                    recurrenceByDay = if (!form.isRecurringInstance) form.recurrenceByDay else null,
                    recurrenceByMonthday = if (!form.isRecurringInstance) form.recurrenceByMonthday else null,
                    recurrenceByMonth = if (!form.isRecurringInstance) form.recurrenceByMonth else null,
                )
                repo.updateEvent(id, request)
                scheduleReminderIfNeeded(id, form.title, startTimeStr, form.reminderMinutes)
                _formState.update { it.copy(isSaving = false, isSaved = true) }
                ScheduleWidget.notifyDataChanged(getApplication())
                SyncWorker.enqueueOneTime(getApplication())
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    private fun scheduleReminderIfNeeded(eventId: String, title: String, startTimeStr: String, reminderMinutes: Int) {
        val context = getApplication<Application>()
        if (reminderMinutes > 0) {
            val ldt = nu.staldal.mycal.util.DateUtils.parseToLocalDateTime(startTimeStr) ?: return
            val triggerMillis = ldt.minusMinutes(reminderMinutes.toLong())
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            NotificationScheduler.scheduleNotification(context, eventId, title, triggerMillis)
        } else {
            NotificationScheduler.cancelNotification(context, eventId)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun buildTimestamps(form: EventFormState): Pair<String, String>? {
        if (form.title.isBlank() || form.startDate.isBlank() || form.endDate.isBlank()) {
            _formState.update { it.copy(error = "Title, start date, and end date are required") }
            return null
        }
        return if (form.allDay) {
            form.startDate to form.endDate
        } else {
            if (form.startTime.isBlank() || form.endTime.isBlank()) {
                _formState.update { it.copy(error = "Start time and end time are required for non-all-day events") }
                return null
            }
            val startLdt = java.time.LocalDateTime.parse("${form.startDate}T${form.startTime}")
            val endLdt = java.time.LocalDateTime.parse("${form.endDate}T${form.endTime}")
            nu.staldal.mycal.util.DateUtils.toRfc3339(startLdt) to nu.staldal.mycal.util.DateUtils.toRfc3339(endLdt)
        }
    }
}
