package nu.staldal.mycal

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.data.preferences.UserPreferences
import nu.staldal.mycal.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MyCalApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            UserPreferences(this@MyCalApplication).migrateCredentialsFromDataStore()
        }
        SyncWorker.enqueuePeriodic(this)
    }
}
