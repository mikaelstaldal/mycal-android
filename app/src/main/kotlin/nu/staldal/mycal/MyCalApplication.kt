package nu.staldal.mycal

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.data.sync.SyncWorker

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class MyCalApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        SyncWorker.enqueuePeriodic(this)
    }
}
