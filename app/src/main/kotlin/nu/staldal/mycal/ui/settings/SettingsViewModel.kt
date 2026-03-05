package nu.staldal.mycal.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import nu.staldal.mycal.data.api.ApiService
import nu.staldal.mycal.data.api.PreferencesDto
import nu.staldal.mycal.data.api.RetrofitClient
import nu.staldal.mycal.data.preferences.ServerConfig
import nu.staldal.mycal.data.preferences.UserPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val testResult: String? = null,
    val isTesting: Boolean = false,
    val defaultEventColor: String = "dodgerblue",
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPreferences(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.serverConfig.first().let { config ->
                _uiState.update {
                    it.copy(baseUrl = config.baseUrl, username = config.username, password = config.password)
                }
            }
        }
        viewModelScope.launch {
            prefs.defaultEventColor.first().let { color ->
                _uiState.update { it.copy(defaultEventColor = color) }
            }
            fetchPreferencesFromBackend()
        }
    }

    private suspend fun getApiService(): ApiService? {
        val config = prefs.serverConfig.first()
        return RetrofitClient.getApiService(config.baseUrl, config.username, config.password)
    }

    private suspend fun fetchPreferencesFromBackend() {
        try {
            val api = getApiService() ?: return
            val response = api.getPreferences()
            if (response.isSuccessful) {
                response.body()?.let { prefsDto ->
                    prefs.saveDefaultEventColor(prefsDto.defaultEventColor)
                    _uiState.update { it.copy(defaultEventColor = prefsDto.defaultEventColor) }
                }
            }
        } catch (_: Exception) {
            // Silently ignore - use local value
        }
    }

    fun updateBaseUrl(url: String) {
        _uiState.update { it.copy(baseUrl = url) }
    }

    fun updateUsername(username: String) {
        _uiState.update { it.copy(username = username) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun updateDefaultEventColor(color: String) {
        _uiState.update { it.copy(defaultEventColor = color) }
        viewModelScope.launch {
            prefs.saveDefaultEventColor(color)
            try {
                getApiService()?.updatePreferences(PreferencesDto(defaultEventColor = color))
            } catch (_: Exception) {
                // Silently ignore - local value is saved
            }
        }
    }

    fun save(onSaved: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            prefs.saveServerConfig(state.baseUrl, state.username, state.password)
            onSaved()
        }
    }

    fun testConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            try {
                val state = _uiState.value
                val api = RetrofitClient.getApiService(state.baseUrl, state.username, state.password)
                if (api == null) {
                    _uiState.update { it.copy(isTesting = false, testResult = "Invalid server URL") }
                    return@launch
                }
                val response = api.listEvents(
                    from = "2020-01-01T00:00:00Z",
                    to = "2020-01-02T00:00:00Z",
                )
                if (response.isSuccessful) {
                    _uiState.update { it.copy(isTesting = false, testResult = "Connection successful!") }
                } else {
                    _uiState.update { it.copy(isTesting = false, testResult = "Error: ${response.code()} ${response.message()}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isTesting = false, testResult = "Error: ${e.message}") }
            }
        }
    }
}
