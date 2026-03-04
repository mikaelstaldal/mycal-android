package nu.staldal.mycal.ui.calendar

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nu.staldal.mycal.MyCalApplication
import nu.staldal.mycal.data.ConnectivityObserver
import nu.staldal.mycal.data.EventRepository
import nu.staldal.mycal.data.api.EventDto
import nu.staldal.mycal.data.api.RetrofitClient
import nu.staldal.mycal.data.preferences.ServerConfig
import nu.staldal.mycal.data.preferences.UserPreferences
import nu.staldal.mycal.data.sync.SyncWorker
import nu.staldal.mycal.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

enum class ViewMode { SCHEDULE, MONTH }

data class CalendarUiState(
    val viewMode: ViewMode = ViewMode.SCHEDULE,
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
    val scheduleEvents: List<EventDto> = emptyList(),
    val scheduleStartMonth: YearMonth = YearMonth.now(),
    val scheduleEndMonth: YearMonth = YearMonth.now(),
    val isLoadingMore: Boolean = false,
    val isOnline: Boolean = true,
    val pendingChangesCount: Int = 0,
    val isOfflineMode: Boolean = false,
    val scrollToTodayTrigger: Int = 0,
)

class CalendarViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val database = (application as MyCalApplication).database
    private val connectivityObserver = ConnectivityObserver(application)
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private var serverConfig = ServerConfig()
    private var monthEventsJob: Job? = null
    private var scheduleEventsJob: Job? = null

    private val repository = EventRepository(database) {
        RetrofitClient.getApiService(serverConfig.baseUrl, serverConfig.username, serverConfig.password)
    }

    init {
        viewModelScope.launch {
            connectivityObserver.isOnline.collect { online ->
                val wasOffline = !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online, error = if (!online) null else it.error) }
                if (online && wasOffline && _uiState.value.isConfigured) {
                    refresh()
                }
            }
        }

        viewModelScope.launch {
            repository.getPendingChangeCount().collect { count ->
                _uiState.update { it.copy(pendingChangesCount = count) }
            }
        }

        viewModelScope.launch {
            prefs.serverConfig.collect { config ->
                serverConfig = config
                _uiState.update { it.copy(isConfigured = config.isConfigured, isOfflineMode = config.offlineMode) }
                if (config.isConfigured) {
                    collectMonthEvents()
                    collectScheduleEvents()
                    if (!config.offlineMode) {
                        refreshEvents()
                    }
                }
            }
        }
    }

    fun previousMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.minusMonths(1)) }
        collectMonthEvents()
        refreshEvents()
    }

    fun nextMonth() {
        _uiState.update { it.copy(currentMonth = it.currentMonth.plusMonths(1)) }
        collectMonthEvents()
        refreshEvents()
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

    fun toggleViewMode() {
        _uiState.update {
            it.copy(
                viewMode = if (it.viewMode == ViewMode.SCHEDULE) ViewMode.MONTH else ViewMode.SCHEDULE,
            )
        }
    }

    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun jumpToToday() {
        if (_uiState.value.viewMode == ViewMode.SCHEDULE) {
            _uiState.update { it.copy(scrollToTodayTrigger = it.scrollToTodayTrigger + 1) }
            collectScheduleEvents()
            refreshScheduleEvents()
        } else {
            val today = LocalDate.now()
            _uiState.update { it.copy(currentMonth = YearMonth.from(today), selectedDate = today) }
            collectMonthEvents()
            refreshEvents()
        }
    }

    fun refresh() {
        if (_uiState.value.viewMode == ViewMode.SCHEDULE) {
            refreshScheduleEvents()
        } else {
            refreshEvents()
        }
    }

    private fun collectMonthEvents() {
        monthEventsJob?.cancel()
        val month = _uiState.value.currentMonth
        val from = DateUtils.toRfc3339(month.atDay(1).atStartOfDay())
        val to = DateUtils.toRfc3339(month.plusMonths(1).atDay(1).atStartOfDay())

        monthEventsJob = viewModelScope.launch {
            repository.getEventsBetween(from, to).collect { events ->
                val selectedDate = _uiState.value.selectedDate
                _uiState.update {
                    it.copy(
                        events = events,
                        selectedDayEvents = events.filter { event ->
                            val eventDate = DateUtils.parseToLocalDate(event.startTime)
                            eventDate == selectedDate
                        },
                    )
                }
            }
        }
    }

    private fun collectScheduleEvents() {
        scheduleEventsJob?.cancel()
        val now = YearMonth.now()
        val startMonth = now
        val endMonth = now.plusMonths(2)
        val from = DateUtils.toRfc3339(startMonth.atDay(1).atStartOfDay())
        val to = DateUtils.toRfc3339(endMonth.plusMonths(1).atDay(1).atStartOfDay())

        _uiState.update { it.copy(scheduleStartMonth = startMonth, scheduleEndMonth = endMonth) }

        scheduleEventsJob = viewModelScope.launch {
            repository.getEventsBetween(from, to).collect { events ->
                _uiState.update { it.copy(scheduleEvents = events) }
            }
        }
    }

    private fun refreshEvents() {
        if (_uiState.value.isOfflineMode || !_uiState.value.isOnline) return

        val month = _uiState.value.currentMonth
        val from = DateUtils.toRfc3339(month.atDay(1).atStartOfDay())
        val to = DateUtils.toRfc3339(month.plusMonths(1).atDay(1).atStartOfDay())

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.refreshEvents(from, to)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun refreshScheduleEvents() {
        if (_uiState.value.isOfflineMode || !_uiState.value.isOnline) return

        val state = _uiState.value
        val from = DateUtils.toRfc3339(state.scheduleStartMonth.atDay(1).atStartOfDay())
        val to = DateUtils.toRfc3339(state.scheduleEndMonth.plusMonths(1).atDay(1).atStartOfDay())

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.refreshEvents(from, to)
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadMoreScheduleEvents(loadNext: Boolean) {
        if (_uiState.value.isLoadingMore) return

        val state = _uiState.value
        val targetMonth = if (loadNext) state.scheduleEndMonth.plusMonths(1) else state.scheduleStartMonth.minusMonths(1)

        // Update range and re-collect
        _uiState.update {
            it.copy(
                scheduleStartMonth = if (loadNext) it.scheduleStartMonth else targetMonth,
                scheduleEndMonth = if (loadNext) targetMonth else it.scheduleEndMonth,
                isLoadingMore = true,
            )
        }

        // Re-collect with the wider range
        scheduleEventsJob?.cancel()
        val updatedState = _uiState.value
        val from = DateUtils.toRfc3339(updatedState.scheduleStartMonth.atDay(1).atStartOfDay())
        val to = DateUtils.toRfc3339(updatedState.scheduleEndMonth.plusMonths(1).atDay(1).atStartOfDay())

        scheduleEventsJob = viewModelScope.launch {
            repository.getEventsBetween(from, to).collect { events ->
                _uiState.update { it.copy(scheduleEvents = events, isLoadingMore = false) }
            }
        }

        // Also refresh from server for the new month
        if (!_uiState.value.isOfflineMode && _uiState.value.isOnline) {
            val targetFrom = DateUtils.toRfc3339(targetMonth.atDay(1).atStartOfDay())
            val targetTo = DateUtils.toRfc3339(targetMonth.plusMonths(1).atDay(1).atStartOfDay())

            viewModelScope.launch {
                try {
                    repository.refreshEvents(targetFrom, targetTo)
                } catch (_: Exception) {
                    // Silently fail — local data will still be shown
                }
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    fun loadEvents() {
        collectMonthEvents()
        refreshEvents()
    }

    fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            try {
                val results = repository.searchEvents(query)
                _uiState.update { it.copy(isSearching = false, searchResults = results) }
            } catch (_: Exception) {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", isSearching = false, searchResults = emptyList()) }
    }

    fun enableOfflineMode() {
        viewModelScope.launch {
            prefs.saveOfflineMode(true)
        }
    }

    fun syncNow() {
        if (_uiState.value.isOfflineMode) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                repository.syncPendingChanges()
                // Refresh current view after sync
                val state = _uiState.value
                if (state.viewMode == ViewMode.SCHEDULE) {
                    val from = DateUtils.toRfc3339(state.scheduleStartMonth.atDay(1).atStartOfDay())
                    val to = DateUtils.toRfc3339(state.scheduleEndMonth.plusMonths(1).atDay(1).atStartOfDay())
                    repository.refreshEvents(from, to)
                } else {
                    val month = state.currentMonth
                    val from = DateUtils.toRfc3339(month.atDay(1).atStartOfDay())
                    val to = DateUtils.toRfc3339(month.plusMonths(1).atDay(1).atStartOfDay())
                    repository.refreshEvents(from, to)
                }
                _uiState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
