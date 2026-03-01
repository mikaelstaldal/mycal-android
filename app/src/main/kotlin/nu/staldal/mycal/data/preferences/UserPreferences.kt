package nu.staldal.mycal.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import nu.staldal.mycal.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class ServerConfig(
    val baseUrl: String = "",
    val username: String = "",
    val password: String = "",
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
}

class UserPreferences(private val context: Context) {
    companion object {
        val BASE_URL = stringPreferencesKey("base_url")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
    }

    val serverConfig: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        ServerConfig(
            baseUrl = prefs[BASE_URL] ?: "",
            username = prefs[USERNAME] ?: "",
            password = prefs[PASSWORD] ?: "",
        )
    }

    suspend fun saveServerConfig(baseUrl: String, username: String, password: String) {
        context.dataStore.edit { prefs ->
            prefs[BASE_URL] = baseUrl
            prefs[USERNAME] = username
            prefs[PASSWORD] = password
        }
    }
}
