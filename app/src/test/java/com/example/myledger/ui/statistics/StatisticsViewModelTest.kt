package com.example.myledger.ui.statistics

import android.app.Application
import android.os.Looper
import androidx.lifecycle.*
import androidx.room.Room
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import java.time.YearMonth
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
class StatisticsViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository
    private var today = LocalDate.of(2026, 12, 31)
    private val stores = mutableListOf<ViewModelStore>()
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.seedCategories).build()
        repository = LedgerRepository(database)
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }; shadowOf(Looper.getMainLooper()).idle(); database.close()
    }
    private fun create(saved: SavedStateHandle = SavedStateHandle()): StatisticsViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return StatisticsViewModel(repository, saved) { today } as T
            }
        }
        return ViewModelProvider.create(store, factory)[StatisticsViewModel::class.java].also { await { !it.state.value.isLoading } }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) fail("Timed out waiting for statistics")
            Thread.sleep(10)
        }
    }
    private fun row(amount: Long = 1234, date: LocalDate = today) = TransactionEntity(type = TransactionType.EXPENSE,
        amountMinor = amount, categoryId = 1, date = date)

    @Test fun defaultsToDailyRestoresAllAndInvalidSavedScopeFallsBackSafely() {
        assertEquals(ExpenseScope.DAILY, create().state.value.scope)
        val saved = SavedStateHandle(); val vm = create(saved); vm.setScope(ExpenseScope.ALL)
        await { !vm.state.value.isLoading }; assertEquals("ALL", saved.get<String>("expenseScope"))
        assertEquals(ExpenseScope.ALL, create(SavedStateHandle(mapOf("expenseScope" to saved.get<String>("expenseScope")))).state.value.scope)
        assertEquals(ExpenseScope.DAILY, create(SavedStateHandle(mapOf("expenseScope" to "invalid"))).state.value.scope)
    }
    @Test fun insertEditCategoryMoveAcrossMonthAndDeleteRefreshAllSections() {
        val vm = create(); val id = runBlocking { repository.addTransaction(row()) }
        await { vm.state.value.summary?.totalMinor == 1234L }
        assertEquals("餐饮", vm.state.value.categoryNames[1])
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(amountMinor = 1801, categoryId = 2)) }
        await { vm.state.value.summary?.categories?.singleOrNull()?.categoryId == 2L }
        assertEquals(1801L, vm.state.value.summary!!.totalMinor); assertEquals(1801L, vm.state.value.summary!!.trend.last().amountMinor)
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(date = today.minusMonths(1))) }
        await { vm.state.value.summary?.totalMinor == 0L && vm.state.value.summary?.trend?.get(4)?.amountMinor == 1801L }
        assertTrue(vm.state.value.summary!!.categories.isEmpty())
        runBlocking { repository.deleteTransaction(id) }
        await { vm.state.value.summary?.trend?.all { it.amountMinor == 0L } == true }
    }
    @Test fun changingActivityAndReimbursableFlagsUsesSharedScopeWithoutManualReload() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "出差", type = ActivityType.WORK)) }
        val id = runBlocking { repository.addTransaction(row().copy(activityId = activity, reimbursable = true)) }
        val vm = create(); assertEquals(0L, vm.state.value.summary!!.totalMinor)
        vm.setScope(ExpenseScope.ALL); await { vm.state.value.summary?.totalMinor == 1234L }
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(activityId = null)) }
        vm.setScope(ExpenseScope.DAILY); await { !vm.state.value.isLoading }; assertEquals(0L, vm.state.value.summary!!.totalMinor)
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(reimbursable = false)) }
        await { vm.state.value.summary?.totalMinor == 1234L }
        assertEquals(FinancialAnalysis.summarizeMonth(listOf(runBlocking { repository.getTransaction(id) }!!), YearMonth.from(today)).period!!.expense.dailyMinor,
            vm.state.value.summary!!.totalMinor)
    }
    @Test fun rapidScopeChangesAndNewYearRefreshKeepLatestMonthAndRestoredSelection() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "活动", type = ActivityType.PERSONAL)) }
        runBlocking { repository.addTransactions(listOf(row(100), row(200, today.plusDays(1)).copy(activityId = activity))) }
        val vm = create()
        vm.setScope(ExpenseScope.ALL); vm.setScope(ExpenseScope.DAILY); vm.setScope(ExpenseScope.ALL)
        today = today.plusDays(1); vm.refreshMonth(); vm.reload()
        await { !vm.state.value.isLoading && vm.state.value.summary?.totalMinor == 200L }
        assertEquals(YearMonth.of(2027, 1), vm.state.value.month); assertEquals(ExpenseScope.ALL, vm.state.value.scope)
        assertEquals(100L, vm.state.value.summary!!.trend[4].amountMinor)
        assertEquals(vm.state.value.month, vm.state.value.summary!!.month); assertEquals(vm.state.value.scope, vm.state.value.summary!!.scope)
    }
    @Test fun ordinaryIncomeAndReimbursementEvenIfOverflowingDoNotAffectExpenseStatistics() {
        runBlocking { repository.addTransactions(listOf(row(1234), row(Long.MAX_VALUE).copy(type = TransactionType.INCOME, categoryId = 12),
            row(1).copy(type = TransactionType.INCOME, categoryId = 12), row(Long.MAX_VALUE).copy(type = TransactionType.REIMBURSEMENT, categoryId = 16))) }
        val vm = create(); assertEquals(1234L, vm.state.value.summary!!.totalMinor); assertFalse(vm.state.value.loadFailed)
        vm.setScope(ExpenseScope.ALL); await { !vm.state.value.isLoading }; assertEquals(1234L, vm.state.value.summary!!.totalMinor)
    }
    @Test fun overflowingAllCanSwitchToRepresentableDailyAndRecoverAfterDeletion() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "大额活动", type = ActivityType.WORK)) }
        val ids = runBlocking { repository.addTransactions(listOf(row(1234), row(Long.MAX_VALUE).copy(activityId = activity))) }
        val vm = create(); assertEquals(1234L, vm.state.value.summary!!.totalMinor)
        vm.setScope(ExpenseScope.ALL); await { !vm.state.value.isLoading }; assertNull(vm.state.value.summary!!.totalMinor)
        assertFalse(vm.state.value.loadFailed)
        vm.setScope(ExpenseScope.DAILY); await { !vm.state.value.isLoading }; assertEquals(1234L, vm.state.value.summary!!.totalMinor)
        runBlocking { repository.deleteTransaction(ids[1]) }
        vm.setScope(ExpenseScope.ALL); await { vm.state.value.summary?.totalMinor == 1234L }
    }
}
