package nu.staldal.mycal.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.RetrofitClient
import nu.staldal.mycal.data.preferences.ServerConfig
import nu.staldal.mycal.data.preferences.UserPreferences
import nu.staldal.mycal.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val events: List<EventDto> = emptyList(),
    val selectedDayEvents: List<EventDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val isConfigured: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<EventDto> = emptyList(),
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var serverConfig = ServerConfig()

    init {
        viewModelScope.launch {
            prefs.serverConfig.collect { config ->
                serverConfig = config
                _uiState.update { it.copy(isConfigured = config.isConfigured) }
                if (config.isConfigured) {
                    loadEvents()
                }
            }
        }
    }

    fun previousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
        loadEvents()
    }

    fun nextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
        loadEvents()
    }

    fun selectDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(
                selectedDate = date,
                selectedDayEvents = state.events.filter { event ->
                    val eventDate = DateUtils.parseToLocalDate(event.startTime)
                    eventDate == date
                },
            )
        }
    }

    fun refresh() {
        loadEvents()
    }

    fun loadEvents() {
        val api = RetrofitClient.getApiService(serverConfig.baseUrl, serverConfig.username, serverConfig.password)
            ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val month = _uiState.value.currentMonth
                val from = month.atDay(1).atStartOfDay()
                val to = month.plusMonths(1).atDay(1).atStartOfDay()
                val fromStr = DateUtils.toRfc3339(from)
                val toStr = DateUtils.toRfc3339(to)

                val response = api.listEvents(fromStr, toStr)
                if (response.isSuccessful) {
                    val events = response.body() ?: emptyList()
                    val selectedDate = _uiState.value.selectedDate
                    _uiState.update {
                        it.copy(
                            events = events,
                            selectedDayEvents = events.filter { event ->
                                val eventDate = DateUtils.parseToLocalDate(event.startTime)
                                eventDate == selectedDate
                            },
                            isLoading = false,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = "Error: ${response.code()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }

        val api = RetrofitClient.getApiService(serverConfig.baseUrl, serverConfig.username, serverConfig.password)
            ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val response = api.searchEvents(query)
                if (response.isSuccessful) {
                    _uiState.update {
                        it.copy(isSearching = false, searchResults = response.body() ?: emptyList())
                    }
                } else {
                    _uiState.update { it.copy(isSearching = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", isSearching = false, searchResults = emptyList()) }
    }
}
