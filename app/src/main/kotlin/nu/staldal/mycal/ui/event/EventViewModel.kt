package nu.staldal.mycal.ui.event

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nu.staldal.mycal.data.api.*
import nu.staldal.mycal.data.preferences.UserPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class EventDetailState(
    val event: EventDto? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isDeleted: Boolean = false,
)

data class EventFormState(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val startDate: String = "", // yyyy-MM-dd
    val startTime: String = "", // HH:mm
    val endDate: String = "",   // yyyy-MM-dd
    val endTime: String = "",   // HH:mm
    val allDay: Boolean = false,
    val color: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
    val isLoading: Boolean = false,
)

class EventViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val _detailState = MutableStateFlow(EventDetailState())
    val detailState: StateFlow<EventDetailState> = _detailState.asStateFlow()

    private val _formState = MutableStateFlow(EventFormState())
    val formState: StateFlow<EventFormState> = _formState.asStateFlow()

    private val serverConfigDeferred = viewModelScope.async {
        prefs.serverConfig.first()
    }

    private suspend fun getApi(): ApiService? {
        val config = serverConfigDeferred.await()
        return RetrofitClient.getApiService(config.baseUrl, config.username, config.password)
    }

    fun loadEvent(id: Long) {
        viewModelScope.launch {
            val api = getApi() ?: return@launch
            _detailState.update { it.copy(isLoading = true, error = null) }
            try {
                val response = api.getEvent(id)
                if (response.isSuccessful) {
                    _detailState.update { it.copy(event = response.body(), isLoading = false) }
                } else {
                    _detailState.update { it.copy(isLoading = false, error = "Error: ${response.code()}") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            val api = getApi() ?: return@launch
            _detailState.update { it.copy(isLoading = true) }
            try {
                val response = api.deleteEvent(id)
                if (response.isSuccessful) {
                    _detailState.update { it.copy(isDeleted = true, isLoading = false) }
                } else {
                    _detailState.update { it.copy(isLoading = false, error = "Delete failed: ${response.code()}") }
                }
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadEventForEdit(id: Long) {
        viewModelScope.launch {
            val api = getApi() ?: return@launch
            _formState.update { it.copy(isLoading = true) }
            try {
                val response = api.getEvent(id)
                if (response.isSuccessful) {
                    val event = response.body()!!
                    val startLdt = nu.staldal.mycal.util.DateUtils.parseToLocalDateTime(event.startTime)
                    val endLdt = nu.staldal.mycal.util.DateUtils.parseToLocalDateTime(event.endTime)
                    _formState.update {
                        it.copy(
                            title = event.title,
                            description = event.description,
                            location = event.location,
                            startDate = startLdt?.toLocalDate()?.toString() ?: "",
                            startTime = if (event.allDay) "" else startLdt?.toLocalTime()?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                            endDate = endLdt?.toLocalDate()?.toString() ?: "",
                            endTime = if (event.allDay) "" else endLdt?.toLocalTime()?.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) ?: "",
                            allDay = event.allDay,
                            color = event.color,
                            isLoading = false,
                        )
                    }
                } else {
                    _formState.update { it.copy(isLoading = false, error = "Error: ${response.code()}") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateTitle(value: String) { _formState.update { it.copy(title = value) } }
    fun updateDescription(value: String) { _formState.update { it.copy(description = value) } }
    fun updateLocation(value: String) { _formState.update { it.copy(location = value) } }
    fun updateStartDate(value: String) { _formState.update { it.copy(startDate = value) } }
    fun updateStartTime(value: String) { _formState.update { it.copy(startTime = value) } }
    fun updateEndDate(value: String) { _formState.update { it.copy(endDate = value) } }
    fun updateEndTime(value: String) { _formState.update { it.copy(endTime = value) } }
    fun updateAllDay(value: Boolean) { _formState.update { it.copy(allDay = value) } }
    fun updateColor(value: String) { _formState.update { it.copy(color = value) } }

    fun createEvent() {
        val form = _formState.value

        val (startTimeStr, endTimeStr) = buildTimestamps(form) ?: return

        viewModelScope.launch {
            val api = getApi() ?: return@launch
            _formState.update { it.copy(isSaving = true, error = null) }
            try {
                val request = CreateEventRequest(
                    title = form.title,
                    description = form.description,
                    location = form.location,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    allDay = form.allDay,
                    color = form.color,
                )
                val response = api.createEvent(request)
                if (response.isSuccessful) {
                    _formState.update { it.copy(isSaving = false, isSaved = true) }
                } else {
                    _formState.update { it.copy(isSaving = false, error = "Error: ${response.code()}") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun updateEvent(id: Long) {
        val form = _formState.value

        val (startTimeStr, endTimeStr) = buildTimestamps(form) ?: return

        viewModelScope.launch {
            val api = getApi() ?: return@launch
            _formState.update { it.copy(isSaving = true, error = null) }
            try {
                val request = UpdateEventRequest(
                    title = form.title,
                    description = form.description,
                    location = form.location,
                    startTime = startTimeStr,
                    endTime = endTimeStr,
                    allDay = form.allDay,
                    color = form.color,
                )
                val response = api.updateEvent(id, request)
                if (response.isSuccessful) {
                    _formState.update { it.copy(isSaving = false, isSaved = true) }
                } else {
                    _formState.update { it.copy(isSaving = false, error = "Error: ${response.code()}") }
                }
            } catch (e: Exception) {
                _formState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
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
