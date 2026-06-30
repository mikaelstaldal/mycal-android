package nu.staldal.mycal.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import nu.staldal.mycal.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
    val offlineMode: Boolean = false,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank() || offlineMode
}

class UserPreferences(private val context: Context) {
    private val credentialStore = CredentialStore(context)

    companion object {
        val OFFLINE_MODE = booleanPreferencesKey("offline_mode")
        val DEFAULT_EVENT_COLOR = stringPreferencesKey("default_event_color")
        val HIDDEN_CALENDAR_IDS = stringSetPreferencesKey("hidden_calendar_ids")
    }

    val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            baseUrl = credentialStore.baseUrl ?: "",
            username = credentialStore.username ?: "",
            password = credentialStore.password ?: "",
            offlineMode = prefs[OFFLINE_MODE] ?: false,
        )
    }

    suspend fun saveServerConfig(baseUrl: String, username: String, password: String) {
        credentialStore.save(baseUrl, username, password)
        context.dataStore.edit { prefs ->
            prefs[OFFLINE_MODE] = false
        }
    }

    suspend fun migrateCredentialsFromDataStore() {
        if (credentialStore.hasCredentials()) return

        val legacyBaseUrl = stringPreferencesKey("base_url")
        val legacyUsername = stringPreferencesKey("username")
        val legacyPassword = stringPreferencesKey("password")

        val snapshot = context.dataStore.data.first()
        val oldBaseUrl = snapshot[legacyBaseUrl]
        val oldUsername = snapshot[legacyUsername]
        val oldPassword = snapshot[legacyPassword]

        if (oldBaseUrl.isNullOrEmpty() && oldUsername.isNullOrEmpty() && oldPassword.isNullOrEmpty()) return

        credentialStore.save(
            baseUrl = oldBaseUrl ?: "",
            username = oldUsername ?: "",
            password = oldPassword ?: "",
        )

        // Overwrite with empty strings before removing for best-effort secure erasure
        context.dataStore.edit { prefs ->
            prefs[legacyBaseUrl] = ""
            prefs[legacyUsername] = ""
            prefs[legacyPassword] = ""
        }
        context.dataStore.edit { prefs ->
            prefs.remove(legacyBaseUrl)
            prefs.remove(legacyUsername)
            prefs.remove(legacyPassword)
        }
    }

    suspend fun saveOfflineMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[OFFLINE_MODE] = enabled
        }
    }

    val defaultEventColor: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[DEFAULT_EVENT_COLOR] ?: "dodgerblue"
    }

    suspend fun saveDefaultEventColor(color: String) {
        context.dataStore.edit { prefs ->
            prefs[DEFAULT_EVENT_COLOR] = color
        }
    }

    val hiddenCalendarIds: Flow<Set<Int>> = context.dataStore.data.map { prefs ->
        prefs[HIDDEN_CALENDAR_IDS]?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()
    }

    suspend fun saveHiddenCalendarIds(ids: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[HIDDEN_CALENDAR_IDS] = ids.map { it.toString() }.toSet()
        }
    }
}
