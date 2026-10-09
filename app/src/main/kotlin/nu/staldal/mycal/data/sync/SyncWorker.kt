package nu.staldal.mycal.data.sync

import android.content.Context
import androidx.work.*
import nu.staldal.mycal.data.EventRepository
import nu.staldal.mycal.data.api.RetrofitClient
import nu.staldal.mycal.data.local.AppDatabase
import nu.staldal.mycal.data.preferences.UserPreferences
import nu.staldal.mycal.notification.NotificationScheduler
import nu.staldal.mycal.widget.ScheduleWidget
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    context: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val prefs = UserPreferences(applicationContext)
        val config = prefs.serverConfig.first()
        if (!config.isConfigured) return Result.success()

        val database = AppDatabase.getInstance(applicationContext)
        val repository = EventRepository(database, applicationContext) {
            RetrofitClient.getApiService(config.baseUrl, config.username, config.password)
        }

        return try {
            repository.syncPendingChanges()

            NotificationScheduler.rescheduleAllNotifications(applicationContext, database)
            ScheduleWidget.notifyDataChanged(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        private const val ONE_TIME_WORK_NAME = "sync_once"
        private const val PERIODIC_WORK_NAME = "sync_periodic"

        fun enqueueOneTime(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                // Never cancel a running POST: the server may have committed it even though
                // cancellation prevents the worker from retiring its pending CREATE locally.
                .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
        }
    }
}
