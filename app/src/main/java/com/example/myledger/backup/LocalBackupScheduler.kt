package com.example.myledger.backup

import android.content.Context
import androidx.work.*
import com.example.myledger.LedgerApplication
import com.example.myledger.data.settings.SettingsRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalBackupScheduler(context: Context) {
    private val application = context.applicationContext
    companion object { const val WORK_NAME = "myledger.daily.local.backup.v1" }
    suspend fun reconcile(settings: SettingsRepository) = sync(settings.currentBackupPreferences().automatic)
    suspend fun sync(enabled: Boolean) = withContext(Dispatchers.IO) {
        val work = WorkManager.getInstance(application)
        val operation = if (enabled) {
            val request = PeriodicWorkRequestBuilder<LocalBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresStorageNotLow(true).build())
                .build()
            work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else work.cancelUniqueWork(WORK_NAME)
        operation.result.get()
        Unit
    }
}

class LocalBackupWorker(context: Context, parameters: WorkerParameters,
    private val manager: BackupManager, private val settings: SettingsRepository) : CoroutineWorker(context, parameters) {
    constructor(context: Context, parameters: WorkerParameters) : this(context, parameters,
        (context.applicationContext as LedgerApplication).backupManager,
        (context.applicationContext as LedgerApplication).settingsRepository)
    override suspend fun doWork(): Result {
        return try {
            if (settings.currentBackupPreferences().automatic) manager.createLocalBackup()
            Result.success()
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            runCatching { settings.recordBackupResult(null) }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
