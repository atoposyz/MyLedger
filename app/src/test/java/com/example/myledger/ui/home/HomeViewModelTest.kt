package com.example.myledger.ui.home

import android.app.Application
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.myledger.analysis.FinancialAnalysis
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
class HomeViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository
    private val stores = mutableListOf<ViewModelStore>()
    private var today = LocalDate.of(2026, 10, 3)
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.seedCategories).build()
        repository = LedgerRepository(database)
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }; shadowOf(Looper.getMainLooper()).idle(); database.close()
    }
    private fun create(): HomeViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST") return HomeViewModel(repository) { today } as T
            }
        }
        return ViewModelProvider.create(store, factory)[HomeViewModel::class.java].also { vm -> await { !vm.state.value.isLoading } }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) fail("Timed out waiting for home state")
            Thread.sleep(10)
        }
    }
    private fun expense(amount: Long = 1234, date: LocalDate = today) =
        TransactionEntity(type = TransactionType.EXPENSE, amountMinor = amount, categoryId = 1, date = date)

    @Test fun emptyMonthReturnsSharedZeroSummaryAndNoRecords() {
        val vm = create()
        assertEquals(YearMonth.from(today), vm.state.value.month)
        assertEquals(0L, vm.state.value.summary!!.balanceMinor)
        assertEquals(0, vm.state.value.summary!!.recordCount)
        assertTrue(vm.state.value.recentRecords.isEmpty()); assertFalse(vm.state.value.loadFailed)
    }

    @Test fun recentFiveUseBusinessDatesThenIdsAcrossMonthsAndResolveAllDisplayFields() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "上海演唱会", type = ActivityType.PERSONAL)) }
        val dates = listOf(today.minusMonths(1), today, today.minusDays(1), today.plusMonths(1), today, today, today.plusMonths(2))
        val rows = dates.mapIndexed { index, date -> expense(100 + index.toLong(), date).copy(activityId = activity, note = "备注-$index") }
        val ids = runBlocking { repository.addTransactions(rows) }
        val vm = create(); val recent = vm.state.value.recentRecords
        assertEquals(listOf(ids[6], ids[3], ids[5], ids[4], ids[1]), recent.map { it.transaction.id })
        assertEquals("上海演唱会", recent.first().activityName); assertEquals("餐饮", recent.first().categoryName)
        assertEquals("备注-6", recent.first().transaction.note)
        val monthRows = rows.filter { YearMonth.from(it.date) == YearMonth.from(today) }
        assertEquals(FinancialAnalysis.summarize(monthRows, today.withDayOfMonth(1), YearMonth.from(today).atEndOfMonth()), vm.state.value.summary!!.period)
        assertEquals(4, vm.state.value.summary!!.recordCount)
    }

    @Test fun insertUpdateMoveAcrossMonthAndDeleteRefreshTotalsWithoutManualReload() {
        val vm = create(); val id = runBlocking { repository.addTransaction(expense()) }
        await { vm.state.value.summary?.balanceMinor == -1234L }
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(amountMinor = 1801)) }
        await { vm.state.value.summary?.balanceMinor == -1801L }
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(date = today.minusMonths(1))) }
        await { vm.state.value.summary?.recordCount == 0 && vm.state.value.recentRecords.singleOrNull()?.transaction?.date == today.minusMonths(1) }
        assertEquals(0L, vm.state.value.summary!!.balanceMinor)
        runBlocking { repository.updateTransaction(repository.getTransaction(id)!!.copy(date = today,
            type = TransactionType.REIMBURSEMENT, categoryId = 16)) }
        await { vm.state.value.summary?.balanceMinor == 1801L }
        assertEquals(0L, vm.state.value.summary!!.period!!.income.ordinaryMinor)
        runBlocking { repository.deleteTransaction(id) }
        await { vm.state.value.recentRecords.isEmpty() && vm.state.value.summary?.balanceMinor == 0L }
    }

    @Test fun activityRenameAndTypeChangeRefreshNamesButKeepMoneyAndRecords() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "旧活动", type = ActivityType.WORK)) }
        val id = runBlocking { repository.addTransaction(expense().copy(activityId = activity, reimbursable = true)) }
        val vm = create(); val before = vm.state.value.summary; val row = vm.state.value.recentRecords.single().transaction
        runBlocking { repository.updateActivity(repository.getActivity(activity)!!.copy(name = "新活动", type = ActivityType.PERSONAL)) }
        await { vm.state.value.recentRecords.singleOrNull()?.activityName == "新活动" }
        assertEquals(ActivityType.PERSONAL, vm.state.value.activityById[activity]!!.type)
        assertEquals(before, vm.state.value.summary); assertEquals(row, runBlocking { repository.getTransaction(id) })
        assertEquals(0L, before!!.period!!.expense.dailyMinor); assertEquals(1234L, before.activityExpenses.single().amountMinor)
    }

    @Test fun clockRefreshUsesNewNaturalMonthAcrossYearAndKeepsOlderRecentRecords() {
        today = LocalDate.of(2026, 12, 31)
        runBlocking { repository.addTransactions(listOf(expense(100), expense(200, today.plusDays(1)))) }
        val vm = create(); assertEquals(-100L, vm.state.value.summary!!.balanceMinor)
        today = today.plusDays(1); vm.refreshMonth()
        await { !vm.state.value.isLoading && vm.state.value.summary?.balanceMinor == -200L }
        assertEquals(YearMonth.of(2027, 1), vm.state.value.month); assertEquals(2, vm.state.value.recentRecords.size)
        val stable = vm.state.value; today = today.plusDays(1); vm.refreshMonth(); assertEquals(stable, vm.state.value)
        stores.last().clear(); assertEquals(YearMonth.of(2027, 1), create().state.value.month)
    }

    @Test fun rapidMonthChangesAndRetryPublishOnlyLatestMonth() {
        runBlocking { repository.addTransactions(listOf(expense(100), expense(200, today.plusMonths(1)), expense(300, today.plusMonths(2)))) }
        val vm = create(); today = today.plusMonths(1); vm.refreshMonth(); today = today.plusMonths(1); vm.refreshMonth(); vm.reload()
        await { !vm.state.value.isLoading && vm.state.value.summary?.balanceMinor == -300L }
        assertEquals(YearMonth.of(2026, 12), vm.state.value.month); assertEquals(vm.state.value.month, vm.state.value.summary!!.month)
    }

    @Test fun overflowingMonthRetainsEditableRecentRowsAndActivityGroups() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "大额活动", type = ActivityType.WORK)) }
        val ids = runBlocking { repository.addTransactions(listOf(expense(Long.MAX_VALUE).copy(activityId = activity), expense(1).copy(activityId = activity))) }
        val vm = create(); assertNull(vm.state.value.summary!!.period); assertNull(vm.state.value.summary!!.balanceMinor)
        assertFalse(vm.state.value.loadFailed); assertEquals(ids.reversed(), vm.state.value.recentRecords.map { it.transaction.id })
        assertEquals(2, vm.state.value.summary!!.activityExpenses.single().recordCount)
        assertNull(vm.state.value.summary!!.activityExpenses.single().amountMinor)
    }
}
