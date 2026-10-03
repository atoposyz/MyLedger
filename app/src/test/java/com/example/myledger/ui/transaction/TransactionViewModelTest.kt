package com.example.myledger.ui.transaction

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class TransactionViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository
    private val today = LocalDate.of(2026, 10, 3)
    private val stores = mutableListOf<ViewModelStore>()

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.seedCategories).build()
        repository = LedgerRepository(database)
    }

    @After fun tearDown() {
        stores.forEach { it.clear() }
        shadowOf(Looper.getMainLooper()).idle()
        database.close()
    }

    private fun create(handle: SavedStateHandle = SavedStateHandle(), transactionId: Long? = null): TransactionViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TransactionViewModel(repository, handle, today, transactionId) as T
            }
        }
        return ViewModelProvider.create(store, factory)[TransactionViewModel::class.java]
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) fail("Timed out waiting for Room / ViewModel state")
            Thread.sleep(10)
        }
    }

    private fun ready(viewModel: TransactionViewModel) = await { !viewModel.state.value.isLoading }

    @Test fun defaultsToTodayAndSwitchesCategoriesWithoutKeepingIncompatibleCategory() {
        val vm = create(); ready(vm)
        assertEquals(today, vm.state.value.form.date)
        assertEquals("餐饮", vm.state.value.selectedCategory!!.name)
        vm.setCategory(2); vm.setReimbursable(true); vm.setType(TransactionType.INCOME)
        assertEquals(5, vm.state.value.categories.size)
        assertEquals("固定工资", vm.state.value.selectedCategory!!.name)
        assertFalse(vm.state.value.form.reimbursable)
        vm.setCategory(2)
        assertEquals("固定工资", vm.state.value.selectedCategory!!.name)
        vm.setType(TransactionType.REIMBURSEMENT)
        assertEquals(listOf("报销"), vm.state.value.categories.map { it.name })
    }

    @Test fun invalidAmountsStayOnFormAndWriteNothing() {
        val vm = create(); ready(vm)
        for (input in listOf("", "0", "-1", "1.234", "92233720368547758.08")) {
            vm.setAmount(input); vm.save()
            assertTrue(vm.state.value.amountError)
            assertNull(vm.state.value.saved)
        }
        assertTrue(runBlocking { repository.getTransactions(today, today) }.isEmpty())
    }

    @Test fun savesExactAmountSelectedBusinessDateActivityFlagAndNoteOnce() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "会议", type = ActivityType.WORK)) }
        val vm = create(); ready(vm)
        await { vm.state.value.activities.any { it.id == activity } }
        val selectedDate = today.minusDays(5)
        vm.setAmount("12.34"); vm.setCategory(2); vm.setActivity(activity)
        vm.setReimbursable(true); vm.setDate(selectedDate); vm.setNote("  出差打车  ")
        vm.save(); vm.save(); vm.setAmount("999")
        await { vm.state.value.saved != null }
        val saved = runBlocking { repository.getTransaction(vm.state.value.saved!!.id) }!!
        assertEquals(1234L, saved.amountMinor)
        assertEquals(2L, saved.categoryId)
        assertEquals(activity, saved.activityId)
        assertTrue(saved.reimbursable)
        assertEquals(selectedDate, saved.date)
        assertEquals("出差打车", saved.note)
        vm.save()
        assertEquals(1, runBlocking { repository.getTransactions(selectedDate, selectedDate) }.size)
        assertEquals("12.34", vm.state.value.form.amount)
    }

    @Test fun savedStateRestoresDraftAfterRecreationAndRestoresCompletionWithoutResaving() {
        val handle = SavedStateHandle()
        val original = create(handle); ready(original)
        original.setType(TransactionType.INCOME); original.setAmount("88.50")
        original.setCategory(13); original.setDate(today.minusDays(1)); original.setNote("项目酬金")
        stores.last().clear()
        val recreated = create(handle); ready(recreated)
        assertEquals(original.state.value.form, recreated.state.value.form)
        recreated.save(); await { recreated.state.value.saved != null }
        stores.last().clear()
        val completed = create(handle); ready(completed)
        assertEquals(recreated.state.value.saved, completed.state.value.saved)
        completed.save()
        assertEquals(1, runBlocking { repository.getTransactions(today.minusDays(1), today) }.size)
    }

    @Test fun reimbursementPersistsAndUsesUnifiedAnalysisAsReimbursementOnly() {
        val vm = create(); ready(vm)
        vm.setType(TransactionType.REIMBURSEMENT); vm.setAmount("60.01"); vm.save()
        await { vm.state.value.saved != null }
        val summary = runBlocking { FinancialAnalysis(repository).getPeriodSummary(today, today) }
        assertEquals(0L, summary.income.ordinaryMinor)
        assertEquals(6001L, summary.income.reimbursementMinor)
        assertEquals(0L, summary.expense.totalMinor)
        assertEquals(TransactionType.REIMBURSEMENT, runBlocking { repository.getTransaction(vm.state.value.saved!!.id) }!!.type)
    }

    @Test fun deletedActivityFailsSafelyAndCanBeCorrectedAndRetried() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "旅行", type = ActivityType.PERSONAL)) }
        val vm = create(); ready(vm)
        await { vm.state.value.activities.any { it.id == activity } }
        vm.setActivity(activity); vm.setAmount("18")
        runBlocking { repository.deleteActivity(activity) }
        vm.save(); await { vm.state.value.saveFailed }
        assertNull(vm.state.value.saved)
        assertFalse(vm.state.value.isSaving)
        vm.setActivity(null); vm.save(); await { vm.state.value.saved != null }
        assertEquals(1, runBlocking { repository.getTransactions(today, today) }.size)
    }

    @Test fun savedTransactionSurvivesDiskDatabaseReopen() {
        stores.forEach { it.clear() }
        database.close()
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(AppDatabase.NAME)
        try {
            database = AppDatabase.create(context)
            repository = LedgerRepository(database)
            val vm = create(); ready(vm)
            vm.setAmount("0.01"); vm.save(); await { vm.state.value.saved != null }
            val id = vm.state.value.saved!!.id
            stores.forEach { it.clear() }
            shadowOf(Looper.getMainLooper()).idle()
            database.close()
            database = AppDatabase.create(context)
            repository = LedgerRepository(database)
            assertEquals(1L, runBlocking { repository.getTransaction(id) }!!.amountMinor)
        } finally { database.close(); context.deleteDatabase(AppDatabase.NAME) }
    }

    private fun existing(): Long = runBlocking {
        repository.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 1234,
            categoryId = 2, date = today.minusDays(1), reimbursable = true, note = "原备注"))
    }

    @Test fun editorLoadsExistingFieldsAndUpdatesSameRowPreservingCreatedTime() {
        val id = existing(); val original = runBlocking { repository.getTransaction(id) }!!
        val vm = create(transactionId = id); ready(vm)
        assertEquals("12.34", vm.state.value.form.amount); assertEquals(2L, vm.state.value.form.categoryId)
        assertEquals(today.minusDays(1), vm.state.value.form.date); assertTrue(vm.state.value.form.reimbursable)
        assertEquals("原备注", vm.state.value.form.note)
        vm.setAmount("18.01"); vm.setNote("  更新备注  "); vm.setDate(today)
        vm.save(); vm.save(); await { vm.state.value.saved != null }
        val updated = runBlocking { repository.getTransaction(id) }!!
        assertEquals(id, updated.id); assertEquals(1801L, updated.amountMinor); assertEquals(today, updated.date)
        assertEquals("更新备注", updated.note); assertEquals(original.createdAt, updated.createdAt)
        assertTrue(updated.updatedAt >= original.updatedAt)
        assertEquals(1, runBlocking { repository.getTransactions(today.minusDays(1), today) }.size)
    }

    @Test fun changingExpenseToReimbursementUpdatesUnifiedFinancialSummary() {
        val id = existing(); val vm = create(transactionId = id); ready(vm)
        vm.setType(TransactionType.REIMBURSEMENT); vm.setAmount("60.01"); vm.save()
        await { vm.state.value.saved != null }
        val updated = runBlocking { repository.getTransaction(id) }!!
        assertEquals(TransactionType.REIMBURSEMENT, updated.type); assertFalse(updated.reimbursable)
        val summary = runBlocking { FinancialAnalysis(repository).getPeriodSummary(today.minusDays(1), today) }
        assertEquals(0L, summary.expense.totalMinor); assertEquals(0L, summary.income.ordinaryMinor)
        assertEquals(6001L, summary.income.reimbursementMinor)
    }

    @Test fun invalidEditAndCancellingWithoutSaveLeaveOriginalUntouched() {
        val id = existing(); val original = runBlocking { repository.getTransaction(id) }
        val vm = create(transactionId = id); ready(vm)
        vm.setAmount("0"); vm.save(); assertTrue(vm.state.value.amountError)
        assertEquals(original, runBlocking { repository.getTransaction(id) })
        vm.setAmount("99"); vm.setNote("未保存")
        stores.last().clear()
        assertEquals(original, runBlocking { repository.getTransaction(id) })
    }

    @Test fun restoredEditorKeepsUnsavedChangesInsteadOfReloadingDatabaseValues() {
        val id = existing(); val handle = SavedStateHandle()
        val original = create(handle, id); ready(original)
        original.setAmount("88.50"); original.setType(TransactionType.INCOME); original.setCategory(13)
        original.setDate(today); original.setNote("恢复草稿")
        stores.last().clear()
        val restored = create(handle, id); ready(restored)
        assertEquals(original.state.value.form, restored.state.value.form)
        restored.save(); await { restored.state.value.saved != null }; stores.last().clear()
        val completed = create(handle, id); ready(completed); completed.save()
        assertEquals(restored.state.value.saved, completed.state.value.saved)
        assertEquals(8850L, runBlocking { repository.getTransaction(id) }!!.amountMinor)
    }

    @Test fun missingOrConcurrentlyDeletedRecordCannotBecomeANewTransaction() {
        val missing = create(transactionId = 999); ready(missing)
        assertTrue(missing.state.value.missingRecord); missing.setAmount("1"); missing.save(); missing.deleteConfirmed()
        assertNull(missing.state.value.saved)
        val id = existing(); val vm = create(transactionId = id); ready(vm)
        runBlocking { repository.deleteTransaction(id) }
        vm.setAmount("99"); vm.save(); await { vm.state.value.saveFailed }
        assertNull(vm.state.value.saved)
        assertTrue(runBlocking { repository.getTransactions(today.minusDays(1), today) }.isEmpty())
    }

    @Test fun confirmedDeleteRemovesOnlySelectedRecordAndCompletionRestoresIdempotently() {
        val id = existing(); val other = existing(); val handle = SavedStateHandle()
        val vm = create(handle, id); ready(vm)
        assertNotNull(runBlocking { repository.getTransaction(id) })
        vm.deleteConfirmed(); vm.deleteConfirmed(); vm.save(); await { vm.state.value.deleted }
        assertNull(runBlocking { repository.getTransaction(id) }); assertNotNull(runBlocking { repository.getTransaction(other) })
        stores.last().clear(); val restored = create(handle, id); ready(restored)
        assertTrue(restored.state.value.deleted); restored.deleteConfirmed(); restored.save()
        assertNotNull(runBlocking { repository.getTransaction(other) })
    }
}
