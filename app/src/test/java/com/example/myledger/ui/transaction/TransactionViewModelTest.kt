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

    private fun create(handle: SavedStateHandle = SavedStateHandle()): TransactionViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return TransactionViewModel(repository, handle, today) as T
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
}
