package com.example.myledger.ui.activity

import android.app.Application
import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.*
import com.example.myledger.data.repository.ActivityDeleteResult
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
class ActivityViewModelTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: LedgerRepository
    private val stores = mutableListOf<ViewModelStore>()
    private val date = LocalDate.of(2026, 10, 3)
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), AppDatabase::class.java)
            .addCallback(AppDatabase.seedCategories).build()
        repository = LedgerRepository(database)
    }
    @After fun tearDown() {
        stores.forEach { it.clear() }; shadowOf(Looper.getMainLooper()).idle(); database.close()
    }
    private inline fun <reified T : ViewModel> create(crossinline build: () -> T): T {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <V : ViewModel> create(modelClass: Class<V>): V {
                @Suppress("UNCHECKED_CAST") return build() as V
            }
        }
        return ViewModelProvider.create(store, factory)[T::class.java]
    }
    private fun editor(id: Long? = null, handle: SavedStateHandle = SavedStateHandle()) =
        create { ActivityEditorViewModel(repository, handle, id) }.also { vm -> await { !vm.state.value.isLoading } }
    private fun list() = create { ActivityListViewModel(repository) }.also { vm -> await { !vm.state.value.isLoading } }
    private fun restored(handle: SavedStateHandle) = SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) })
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) fail("Timed out waiting for activity state")
            Thread.sleep(10)
        }
    }
    private fun seed(name: String = "上海演唱会") = runBlocking {
        repository.addActivity(ActivityEntity(name = name, type = ActivityType.PERSONAL))
    }
    private fun expense(activity: Long) = TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 1234,
        categoryId = 1, date = date, activityId = activity, note = "午饭")

    @Test fun createsTrimmedOptionalFieldsOnlyOnceAndRestoresCompletion() {
        val handle = SavedStateHandle(); val vm = editor(handle = handle); val list = list()
        vm.setName("  上海演唱会  "); vm.setNote("  门票  "); vm.save(); vm.save()
        await { vm.state.value.savedId != null && list.state.value.activities.size == 1 }
        val saved = runBlocking { repository.getActivity(vm.state.value.savedId!!) }!!
        assertEquals("上海演唱会", saved.name); assertEquals("门票", saved.note)
        assertEquals(ActivityType.PERSONAL, saved.type); assertNull(saved.startDate); assertNull(saved.endDate)
        stores.first().clear()
        val recreated = editor(handle = restored(handle)); assertEquals(saved.id, recreated.state.value.savedId)
        recreated.save(); shadowOf(Looper.getMainLooper()).idle(); assertEquals(1, list.state.value.activities.size)
    }

    @Test fun editingAllFieldsPreservesIdAssociatedRecordsAndFinancialMeaning() {
        val id = seed(); val row = runBlocking { repository.addTransaction(expense(id)) }
        val before = runBlocking { repository.getTransaction(row) }!!
        val vm = editor(id)
        vm.setName("ISCA 2027"); vm.setType(ActivityType.WORK); vm.setStart(date.plusYears(1))
        vm.setEnd(date.plusYears(1).plusDays(2)); vm.setNote("  会议  "); vm.save()
        await { vm.state.value.savedId != null }
        assertEquals(ActivityEntity(id, "ISCA 2027", ActivityType.WORK, date.plusYears(1), date.plusYears(1).plusDays(2), "会议"),
            runBlocking { repository.getActivity(id) })
        assertEquals(before, runBlocking { repository.getTransaction(row) })
        val summary = FinancialAnalysis.summarize(listOf(before), date, date)
        assertEquals(1234L, summary.expense.totalMinor); assertEquals(0L, summary.expense.dailyMinor)
    }

    @Test fun invalidNameAndReversedDatesNeverWriteButEqualAndSingleDatesAreAllowed() {
        val vm = editor(); val list = list()
        vm.setName("  "); vm.save(); assertTrue(vm.state.value.nameError)
        vm.setName("旅行"); vm.setStart(date); vm.setEnd(date.minusDays(1)); vm.save()
        shadowOf(Looper.getMainLooper()).idle(); assertTrue(vm.state.value.form.invalidDates)
        assertTrue(list.state.value.activities.isEmpty())
        vm.setEnd(date); vm.save(); await { vm.state.value.savedId != null }
        val id = vm.state.value.savedId!!; val edit = editor(id)
        edit.setStart(null); edit.setNote("   "); edit.save(); await { edit.state.value.savedId != null }
        val saved = runBlocking { repository.getActivity(id) }!!
        assertNull(saved.startDate); assertEquals(date, saved.endDate); assertNull(saved.note)
    }

    @Test fun draftRestorationKeepsEveryFieldAndNeverOverwritesLoadedEdits() {
        val id = seed(); val handle = SavedStateHandle(); val vm = editor(id, handle)
        vm.setName("草稿"); vm.setType(ActivityType.WORK); vm.setStart(date); vm.setEnd(date.plusDays(1)); vm.setNote("未保存")
        val expected = vm.state.value.form; stores.last().clear()
        val recreated = editor(id, restored(handle)); assertEquals(expected, recreated.state.value.form)
        assertEquals("上海演唱会", runBlocking { repository.getActivity(id) }!!.name)
        val freshHandle = SavedStateHandle(); val fresh = editor(handle = freshHandle)
        fresh.setName("新草稿"); fresh.setStart(date); stores.last().clear()
        assertEquals(fresh.state.value.form, editor(handle = restored(freshHandle)).state.value.form)
    }

    @Test fun returningWithoutSavingKeepsOriginalActivity() {
        val id = seed(); val original = runBlocking { repository.getActivity(id) }
        val vm = editor(id); vm.setName("取消的改名"); vm.setType(ActivityType.WORK); vm.setEnd(date)
        stores.last().clear(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(original, runBlocking { repository.getActivity(id) })
    }

    @Test fun referencedActivityCountsAllTypesAcrossDatesAndCannotBeDeleted() {
        val id = seed()
        val rows = runBlocking { repository.addTransactions(listOf(expense(id),
            expense(id).copy(type = TransactionType.INCOME, categoryId = 13, date = date.minusYears(1)),
            expense(id).copy(type = TransactionType.REIMBURSEMENT, categoryId = 16, date = date.plusYears(1)))) }
        val originals = runBlocking { rows.map { repository.getTransaction(it) } }
        val vm = editor(id); vm.deleteConfirmed(); shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(runBlocking { repository.getActivity(id) })
        vm.requestDelete(); await { vm.state.value.deleteUsage != null }
        assertEquals(3L, vm.state.value.deleteUsage); vm.deleteConfirmed(); shadowOf(Looper.getMainLooper()).idle()
        assertEquals(ActivityDeleteResult.InUse(3), runBlocking { repository.deleteActivityIfUnused(id) })
        assertEquals(originals, runBlocking { rows.map { repository.getTransaction(it) } })
        vm.dismissDelete(); assertNull(vm.state.value.deleteUsage); assertFalse(vm.state.value.deleted)
    }

    @Test fun confirmationRestoresRechecksNewAssociationsAndDeletesOnlyUnusedActivity() {
        val id = seed(); val other = seed("保留"); val handle = SavedStateHandle(); val vm = editor(id, handle)
        vm.requestDelete(); await { vm.state.value.deleteUsage == 0L }
        val row = runBlocking { repository.addTransaction(expense(id)) }
        vm.deleteConfirmed(); await { !vm.state.value.isDeleting && vm.state.value.deleteUsage == 1L }
        assertNotNull(runBlocking { repository.getActivity(id) }); assertNotNull(runBlocking { repository.getTransaction(row) })
        vm.dismissDelete(); runBlocking { repository.updateTransaction(repository.getTransaction(row)!!.copy(activityId = null)) }
        vm.requestDelete(); await { vm.state.value.deleteUsage == 0L }
        stores.last().clear(); val recreated = editor(id, restored(handle))
        assertEquals(0L, recreated.state.value.deleteUsage); recreated.dismissDelete()
        assertNotNull(runBlocking { repository.getActivity(id) })
        recreated.requestDelete(); await { recreated.state.value.deleteUsage == 0L }
        recreated.deleteConfirmed(); recreated.deleteConfirmed(); await { recreated.state.value.deleted }
        assertNull(runBlocking { repository.getActivity(id) }); assertNotNull(runBlocking { repository.getActivity(other) })
        assertNotNull(runBlocking { repository.getTransaction(row) })
        stores.last().clear()
        assertEquals(ActivityDeleteResult.Missing, runBlocking { repository.deleteActivityIfUnused(id) })
    }

    @Test fun deletedCompletionSurvivesRecreationWithoutMissingScreen() {
        val id = seed(); val handle = SavedStateHandle(); val vm = editor(id, handle)
        vm.requestDelete(); await { vm.state.value.deleteUsage == 0L }
        vm.deleteConfirmed(); await { vm.state.value.deleted }; stores.last().clear()
        val recreated = editor(id, restored(handle))
        assertTrue(recreated.state.value.deleted); assertFalse(recreated.state.value.missing)
    }

    @Test fun missingAndStaleEditsNeverBecomeNewActivities() {
        val missing = editor(999); assertTrue(missing.state.value.missing); missing.save()
        val id = seed(); val vm = editor(id); vm.setName("过期编辑")
        runBlocking { repository.deleteActivityIfUnused(id) }; vm.save(); await { vm.state.value.saveFailed }
        assertNull(vm.state.value.savedId); assertTrue(list().state.value.activities.isEmpty())
    }

    @Test fun listFlowRefreshesAfterExternalCreateUpdateAndDelete() {
        val vm = list(); assertTrue(vm.state.value.activities.isEmpty())
        val first = seed(); val second = seed("ISCA 2027")
        await { vm.state.value.activities.size == 2 }
        assertEquals(listOf(second, first), vm.state.value.activities.map { it.id })
        runBlocking { repository.updateActivity(repository.getActivity(first)!!.copy(name = "新名称", type = ActivityType.WORK)) }
        await { vm.state.value.activities.lastOrNull()?.name == "新名称" }
        runBlocking { repository.deleteActivityIfUnused(second) }; await { vm.state.value.activities.size == 1 }
        assertEquals(first, vm.state.value.activities.single().id)
    }
}
