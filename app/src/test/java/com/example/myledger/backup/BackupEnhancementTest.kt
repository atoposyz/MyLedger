package com.example.myledger.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Looper
import androidx.lifecycle.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.work.*
import androidx.work.testing.*
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.*
import com.example.myledger.ui.backup.BackupViewModel
import com.example.myledger.ui.backup.BackupMessage
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupEnhancementTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var settings: SettingsRepository
    private lateinit var manager: BackupManager
    private val job = SupervisorJob()
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private var keyFailed = false
    private val password get() = "Portable-backup-2026".toCharArray()
    @Before fun setup() {
        val base = RuntimeEnvironment.getApplication()
        val directory = File(base.cacheDir, "enhancement-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir() = File(directory, "cache").apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).addCallback(AppDatabase.seedCategories).build()
        ledger = LedgerRepository(database)
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { File(context.filesDir, "settings.preferences_pb") })
        manager = BackupManager(context, ledger, settings, LocalBackupCipher { if (keyFailed) throw IOException("Test key unavailable") else key })
    }
    @After fun close() = runBlocking { database.close(); job.cancelAndJoin() }
    private suspend fun add(amount: Long = 1234) = ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE,
        amountMinor = amount, categoryId = 1, date = LocalDate.of(2026, 10, 3), note = "备份测试长备注".repeat(100)))

    @Test fun encryptedExportUnlocksFromPrivateCopyAndRestoresExactLedgerAndTheme() = runBlocking {
        add(Long.MAX_VALUE); settings.setTheme(ThemeMode.DARK); val original = ledger.snapshot()
        val exported = manager.prepareEncryptedExport(password)
        assertTrue(manager.readExportPreview(exported.fileName).encrypted)
        val source = manager.exportFile(exported.fileName)
        val locked = assertThrows(BackupPasswordRequired::class.java) { runBlocking { manager.prepareImport(Uri.fromFile(source)) } }.fileName
        add(1); settings.setTheme(ThemeMode.LIGHT); val before = ledger.snapshot()
        assertThrows(BackupUnlockException::class.java) { runBlocking { manager.unlockImport(locked, "wrong-password".toCharArray()) } }
        assertEquals(before, ledger.snapshot()); assertTrue(manager.hasLockedImport(locked))
        source.writeText("external selection changed")
        val preview = manager.unlockImport(locked, password)
        assertFalse(manager.hasLockedImport(locked)); assertEquals(before, ledger.snapshot())
        manager.restore(preview.fileName)
        assertEquals(original, ledger.snapshot()); assertEquals(ThemeMode.DARK, settings.current().themeMode)
        manager.restore(manager.prepareSafetyImport().fileName); assertEquals(before, ledger.snapshot())
    }
    @Test fun localHistoryRetainsSevenAndCanRestoreOrExportAnOlderSnapshot() = runBlocking<Unit> {
        val snapshots = mutableListOf<com.example.myledger.data.repository.LedgerSnapshot>()
        repeat(9) { add(1); snapshots += ledger.snapshot(); manager.createLocalBackup() }
        val entries = manager.history.value; assertEquals(7, entries.size)
        assertEquals(listOf(9, 8, 7, 6, 5, 4, 3), entries.map { it.metadata.transactionCount })
        assertEquals(7, File(context.filesDir, "backups/history").listFiles()!!.count { it.extension == "enc" })
        val oldest = entries.last(); val exported = manager.prepareEncryptedExport(password, oldest.fileName)
        val decoded = BackupEncryption.decrypt(manager.exportFile(exported.fileName).readBytes(), password)
        assertEquals(snapshots[2], BackupCodec.read(decoded.inputStream()).ledger)
        val preview = manager.prepareHistoryImport(oldest.fileName); assertEquals(snapshots.last(), ledger.snapshot())
        manager.restore(preview.fileName); assertEquals(snapshots[2], ledger.snapshot())
        manager.deleteHistory(oldest.fileName); assertEquals(6, manager.history.value.size)
        assertEquals(snapshots[2], ledger.snapshot())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { manager.deleteHistory("../index.json") } }
    }
    @Test fun failedNewHistoryOrTamperedBackupDoesNotDestroyExistingCopiesOrLedger() = runBlocking {
        add(); val first = manager.createLocalBackup(); val before = ledger.snapshot(); val history = manager.history.value
        keyFailed = true
        assertThrows(IOException::class.java) { runBlocking { manager.createLocalBackup() } }
        assertEquals(history, manager.history.value); assertEquals(before, ledger.snapshot())
        keyFailed = false
        val file = File(context.filesDir, "backups/history/${first.fileName}")
        file.writeBytes(file.readBytes().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() })
        assertThrows(Exception::class.java) { runBlocking { manager.prepareHistoryImport(first.fileName) } }
        assertEquals(before, ledger.snapshot()); assertTrue(file.isFile)
    }
    @Test fun concurrentHistoryWritesKeepOnlySevenUniqueCompleteFiles() = runBlocking {
        add(); coroutineScope { (1..10).map { async { manager.createLocalBackup() } }.awaitAll() }
        assertEquals(7, manager.history.value.size)
        assertEquals(7, manager.history.value.map { it.fileName }.distinct().size)
        manager.history.value.forEach { assertEquals(1, manager.prepareHistoryImport(it.fileName).metadata.transactionCount) }
    }
    @Test fun oldZipAndPortableExportKeepBackupPreferenceSeparateFromRestoredTheme() = runBlocking {
        add(); val original = ledger.snapshot(); val zip = manager.prepareExport()
        settings.setAutomaticBackup(true); settings.setTheme(ThemeMode.DARK)
        manager.restore(manager.prepareImport(Uri.fromFile(manager.exportFile(zip.fileName))).fileName)
        assertEquals(original, ledger.snapshot()); assertEquals(ThemeMode.SYSTEM, settings.current().themeMode)
        assertTrue(settings.currentBackupPreferences().automatic)
        settings.setTheme(ThemeMode.LIGHT); assertTrue(settings.currentBackupPreferences().automatic)
    }
    private fun worker(attempt: Int = 0): LocalBackupWorker = TestListenableWorkerBuilder<LocalBackupWorker>(context)
        .setRunAttemptCount(attempt).setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                LocalBackupWorker(appContext, workerParameters, manager, settings)
        }).build()
    @Test fun workerHonorsDisabledPreferenceCreatesEncryptedHistoryAndReportsBoundedRetries() = runBlocking {
        add(); assertEquals(ListenableWorker.Result.success(), worker().doWork()); assertTrue(manager.history.value.isEmpty())
        settings.setAutomaticBackup(true)
        assertEquals(ListenableWorker.Result.success(), worker().doWork()); assertEquals(1, manager.history.value.size)
        assertNotNull(settings.currentBackupPreferences().lastSuccessMillis)
        keyFailed = true
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(ListenableWorker.Result.failure(), worker(2).doWork())
        assertTrue(settings.currentBackupPreferences().lastAttemptFailed); assertEquals(1, manager.history.value.size)
        keyFailed = false; assertEquals(ListenableWorker.Result.success(), worker().doWork())
        assertFalse(settings.currentBackupPreferences().lastAttemptFailed)
    }
    @Test fun schedulerIsUniqueSurvivesReconciliationAndCancelsWhenDisabled() = runBlocking {
        val base = RuntimeEnvironment.getApplication()
        WorkManagerTestInitHelper.initializeTestWorkManager(base, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val scheduler = LocalBackupScheduler(base); val work = WorkManager.getInstance(base)
        scheduler.sync(true); scheduler.sync(true)
        val queued = work.getWorkInfosForUniqueWork(LocalBackupScheduler.WORK_NAME).get()
        assertEquals(1, queued.size); assertEquals(WorkInfo.State.ENQUEUED, queued.single().state)
        settings.setAutomaticBackup(false); scheduler.reconcile(settings)
        assertEquals(WorkInfo.State.CANCELLED, work.getWorkInfosForUniqueWork(LocalBackupScheduler.WORK_NAME).get().single().state)
    }
    @Test fun viewModelDoesNotEnableFailedAutomaticBackupAndNeverPersistsPassword() {
        runBlocking { add() }; val original = runBlocking { ledger.snapshot() }
        val saved = SavedStateHandle()
        val stores = mutableListOf<ViewModelStore>()
        fun create(): BackupViewModel {
            val store = ViewModelStore().also(stores::add)
            val factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST") return BackupViewModel(manager, saved, settings, LocalBackupScheduler(context)) as T
                }
            }
            return ViewModelProvider.create(store, factory)[BackupViewModel::class.java]
        }
        fun await(predicate: () -> Boolean) {
            val deadline = System.nanoTime() + 15_000_000_000L
            while (!predicate()) {
                shadowOf(Looper.getMainLooper()).idle()
                if (System.nanoTime() > deadline) fail("Backup ViewModel timed out")
                Thread.sleep(10)
            }
        }
        try {
            val vm = create(); await { !vm.state.value.busy && vm.state.value.preferencesReady }
            keyFailed = true; vm.setAutomatic(true)
            await { !vm.state.value.busy && vm.state.value.message == BackupMessage.AUTO_FAILED }
            assertFalse(runBlocking { settings.currentBackupPreferences().automatic }); assertEquals(original, runBlocking { ledger.snapshot() })
            val secret = password; vm.encryptedExport(secret)
            await { vm.state.value.export?.encrypted == true && secret.all { it == '\u0000' } }
            assertTrue(saved.keys().none { it.contains("password", ignoreCase = true) })
            val exported = vm.state.value.export; stores.first().clear()
            val recreated = create(); await { !recreated.state.value.busy && recreated.state.value.export != null }
            assertEquals(exported, recreated.state.value.export); assertEquals(original, runBlocking { ledger.snapshot() })
        } finally { stores.forEach { it.clear() } }
    }
}
