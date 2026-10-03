package com.example.myledger.backup

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.room.Room
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.*
import com.example.myledger.data.settings.*
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class BackupManagerTest {
    private lateinit var database: AppDatabase
    private lateinit var ledger: LedgerRepository
    private lateinit var settings: SettingsRepository
    private lateinit var manager: BackupManager
    private lateinit var context: Context
    private val job = SupervisorJob()
    private val day = LocalDate.of(2026, 10, 3)
    @Before fun setUp() {
        val base = RuntimeEnvironment.getApplication()
        val directory = File(base.cacheDir, "backup-test-${UUID.randomUUID()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = File(directory, "files").apply { mkdirs() }
            override fun getCacheDir(): File = File(directory, "cache").apply { mkdirs() }
        }
        database = Room.inMemoryDatabaseBuilder(base, AppDatabase::class.java).addCallback(AppDatabase.seedCategories).build()
        ledger = LedgerRepository(database)
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO)) { File(context.filesDir, "settings.preferences_pb") })
        manager = BackupManager(context, ledger, settings)
    }
    @After fun close() = runBlocking { database.close(); job.cancelAndJoin() }
    private suspend fun add(amount: Long = 1234): Long = ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = amount, categoryId = 1, date = day))
    private suspend fun import(export: PreparedBackup) = manager.prepareImport(Uri.fromFile(manager.exportFile(export.fileName)))

    @Test fun exportPreviewDoesNotMutateAndRestorePreservesIdsAuditFieldsReferencesAndFinancialResults() = runBlocking {
        val activity = ledger.addActivity(ActivityEntity(name = "出差", type = ActivityType.WORK))
        ledger.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 1234, categoryId = 1, date = day, activityId = activity, reimbursable = true))
        ledger.addTransaction(TransactionEntity(type = TransactionType.REIMBURSEMENT, amountMinor = 1801, categoryId = 16, date = day, activityId = activity))
        settings.setTheme(ThemeMode.DARK); val original = ledger.snapshot(); val export = manager.prepareExport()
        add(999); settings.setTheme(ThemeMode.LIGHT); val before = ledger.snapshot()
        val preview = import(export); assertEquals(before, ledger.snapshot()); assertEquals(2, preview.metadata.transactionCount)
        manager.restore(preview.fileName)
        assertEquals(original, ledger.snapshot()); assertEquals(ThemeMode.DARK, settings.current().themeMode)
        assertEquals(FinancialAnalysis.summarizeMonth(original.transactions, YearMonth.from(day)), FinancialAnalysis.summarizeMonth(ledger.snapshot().transactions, YearMonth.from(day)))
        assertTrue(manager.hasSafetyCopy)
        val safety = manager.prepareSafetyImport(); manager.restore(safety.fileName)
        assertEquals(before, ledger.snapshot()); assertEquals(ThemeMode.LIGHT, settings.current().themeMode)
        assertTrue(add(1) > before.transactions.maxOf { it.id })
    }
    @Test fun emptyBackupReplacesAllRecordsAndActivitiesButBuiltInCategoriesRemainUsable() = runBlocking {
        val empty = manager.prepareExport()
        add(); ledger.addActivity(ActivityEntity(name = "活动", type = ActivityType.PERSONAL))
        manager.restore(import(empty).fileName)
        val snapshot = ledger.snapshot(); assertTrue(snapshot.transactions.isEmpty()); assertTrue(snapshot.activities.isEmpty()); assertEquals(17, snapshot.categories.size)
        assertTrue(add() > 0)
    }
    @Test fun cancelledPreviewAndInvalidImportLeaveLedgerUnchanged() = runBlocking {
        add(); val original = ledger.snapshot(); val preview = import(manager.prepareExport()); manager.discardImport(preview.fileName)
        assertEquals(original, ledger.snapshot())
        val bad = File(context.cacheDir, "invalid.zip").apply { writeText("bad") }
        assertThrows(BackupFormatException::class.java) { runBlocking { manager.prepareImport(Uri.fromFile(bad)) } }
        assertEquals(original, ledger.snapshot()); assertFalse(manager.hasSafetyCopy)
    }
    @Test fun confirmationUsesPrivateCopyEvenIfSelectedSourceChangesAfterPreview() = runBlocking {
        add(); val original = ledger.snapshot(); val export = manager.prepareExport(); val preview = import(export)
        manager.exportFile(export.fileName).writeText("source changed")
        add(999); manager.restore(preview.fileName); assertEquals(original, ledger.snapshot())
    }
    @Test fun settingsWriteFailureRollsBackWholeRoomReplacementAndKeepsSafetyCopy() = runBlocking {
        add(); val export = manager.prepareExport(); add(999); val original = ledger.snapshot()
        val failing = SettingsRepository(object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences = throw IOException("disk full")
        })
        val failingManager = BackupManager(context, ledger, failing)
        val preview = failingManager.prepareImport(Uri.fromFile(manager.exportFile(export.fileName)))
        assertThrows(IOException::class.java) { runBlocking { failingManager.restore(preview.fileName) } }
        assertEquals(original, ledger.snapshot()); assertTrue(failingManager.hasSafetyCopy)
    }
    @Test fun repositoryReplacementRollbackAndValidationProtectExistingLedger() = runBlocking {
        val id = add(); val original = ledger.snapshot()
        val empty = original.copy(transactions = emptyList())
        assertThrows(IOException::class.java) { runBlocking { ledger.replaceLedger(empty) { throw IOException("before commit") } } }
        assertEquals(original, ledger.snapshot())
        assertThrows(IllegalArgumentException::class.java) { runBlocking { ledger.replaceLedger(original.copy(transactions = listOf(original.transactions.single().copy(amountMinor = 0)))) } }
        assertNotNull(ledger.getTransaction(id)); assertEquals(original, ledger.snapshot())
    }
    @Test fun exportCanBeSavedToChosenFileAndDecodedWithoutDatabaseAccess() = runBlocking {
        add(); val export = manager.prepareExport(); val file = File(context.cacheDir, "chosen.zip")
        manager.saveExport(export.fileName, Uri.fromFile(file))
        assertEquals(ledger.snapshot(), file.inputStream().use { BackupCodec.read(it) }.ledger)
    }
}
