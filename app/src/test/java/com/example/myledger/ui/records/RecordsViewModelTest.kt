package com.example.myledger.ui.records

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.TransactionEntity
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
class RecordsViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository
    private val stores = mutableListOf<ViewModelStore>()
    private val today = LocalDate.of(2026, 10, 3)
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.seedCategories).build()
        repository = LedgerRepository(database)
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }; shadowOf(Looper.getMainLooper()).idle(); database.close()
    }
    private fun create(handle: SavedStateHandle = SavedStateHandle()): RecordsViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return RecordsViewModel(repository, handle) as T
            }
        }
        return ViewModelProvider.create(store, factory)[RecordsViewModel::class.java].also { vm -> await { !vm.state.value.isLoading } }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) fail("Timed out waiting for records Flow")
            Thread.sleep(10)
        }
    }
    private fun expense(date: LocalDate = today) = TransactionEntity(type = TransactionType.EXPENSE,
        amountMinor = 1234, categoryId = 1, date = date)

    @Test fun insertEditDateAndDeleteAutomaticallyRegroupAndRefreshDailyTotals() {
        val vm = create(); assertTrue(vm.state.value.days.isEmpty())
        val id = runBlocking { repository.addTransaction(expense()) }
        await { vm.state.value.days.size == 1 }
        assertEquals(1234L, vm.state.value.days.single().summary!!.expense.totalMinor)
        runBlocking { repository.updateTransaction(requireNotNull(repository.getTransaction(id)).copy(
            amountMinor = 8850, type = TransactionType.INCOME, categoryId = 13, date = today.minusDays(1))) }
        await { vm.state.value.days.singleOrNull()?.date == today.minusDays(1) }
        assertEquals(0L, vm.state.value.days.single().summary!!.expense.totalMinor)
        assertEquals(8850L, vm.state.value.days.single().summary!!.income.ordinaryMinor)
        assertEquals("项目酬金", vm.state.value.days.single().records.single().categoryName)
        runBlocking { repository.deleteTransaction(id) }; await { vm.state.value.days.isEmpty() }
    }
    @Test fun inclusiveRangeSurvivesRecreationAndClearShowsAllAgain() {
        runBlocking { repository.addTransactions((-1L..2L).map { expense(today.plusDays(it)) }) }
        val handle = SavedStateHandle(); val vm = create(handle)
        vm.setRange(RecordDateRange(today, today.plusDays(1)))
        await { !vm.state.value.isLoading && vm.state.value.days.size == 2 }
        assertEquals(listOf(today.plusDays(1), today), vm.state.value.days.map { it.date })
        stores.last().clear(); val restored = create(handle)
        assertEquals(vm.state.value.range, restored.state.value.range); assertEquals(vm.state.value.days, restored.state.value.days)
        restored.setRange(null); await { !restored.state.value.isLoading && restored.state.value.days.size == 4 }
        restored.setRange(RecordDateRange(today.plusYears(1), today.plusYears(1)))
        await { !restored.state.value.isLoading }; assertTrue(restored.state.value.days.isEmpty())
        restored.setRange(RecordDateRange(today.minusDays(1), today.minusDays(1)))
        restored.setRange(RecordDateRange(today.plusDays(2), today.plusDays(2)))
        await { !restored.state.value.isLoading && restored.state.value.days.singleOrNull()?.date == today.plusDays(2) }
    }
    @Test fun activityRenameRefreshesDisplayWithoutChangingTransaction() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "原活动", type = ActivityType.WORK)) }
        runBlocking { repository.addTransaction(expense().copy(activityId = activity, reimbursable = true)) }
        val vm = create(); assertEquals("原活动", vm.state.value.days.single().records.single().activityName)
        runBlocking { repository.updateActivity(requireNotNull(repository.getActivity(activity)).copy(name = "新活动")) }
        await { vm.state.value.days.singleOrNull()?.records?.singleOrNull()?.activityName == "新活动" }
        assertEquals(1234L, vm.state.value.days.single().summary!!.expense.totalMinor)
        assertEquals(0L, vm.state.value.days.single().summary!!.expense.dailyMinor)
    }
}
