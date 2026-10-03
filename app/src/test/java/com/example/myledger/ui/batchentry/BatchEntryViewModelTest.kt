package com.example.myledger.ui.batchentry

import android.app.Application
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
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
class BatchEntryViewModelTest {
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
    private fun create(handle: SavedStateHandle = SavedStateHandle()): BatchEntryViewModel {
        val store = ViewModelStore().also(stores::add)
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BatchEntryViewModel(repository, handle, today) as T
            }
        }
        return ViewModelProvider.create(store, factory)[BatchEntryViewModel::class.java].also { vm ->
            await { !vm.state.value.isLoading }
        }
    }
    private fun await(predicate: () -> Boolean) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!predicate()) {
            shadowOf(Looper.getMainLooper()).idle()
            if (System.nanoTime() > deadline) fail("Timed out waiting for Room / batch state")
            Thread.sleep(10)
        }
    }
    private fun rows() = runBlocking { repository.getTransactions(today.minusDays(10), today.plusDays(10)) }
    private fun activity(type: ActivityType) = runBlocking {
        repository.addActivity(ActivityEntity(name = type.name, type = type))
    }

    @Test fun savesTenIndependentTransactionsExactlyOnceAndUsesSelectedBusinessDate() {
        val vm = create()
        vm.removeDraft(vm.state.value.drafts.single().id)
        vm.setDefaultDate(today.minusDays(3))
        repeat(10) { index ->
            vm.addDraft()
            val id = vm.state.value.drafts.last().id
            vm.setAmount(id, "${index + 1}.01"); vm.setNote(id, "  row-$index  ")
        }
        assertEquals(5510L, vm.state.value.totalMinor)
        vm.saveAll(); vm.saveAll(); vm.addDraft()
        await { vm.state.value.saved != null }
        assertEquals(SavedBatch(10, 5510), vm.state.value.saved)
        val saved = rows()
        assertEquals(10, saved.size)
        assertEquals(10, saved.map { it.id }.distinct().size)
        assertEquals((1..10).map { it * 100L + 1L }.sorted(), saved.map { it.amountMinor }.sorted())
        assertTrue(saved.all { it.date == today.minusDays(3) && it.note!!.startsWith("row-") })
        vm.saveAll(); assertEquals(10, rows().size)
    }

    @Test fun newDraftsInheritDefaultsAndPreviousCategoryButOverridesStayIndependent() {
        val work = activity(ActivityType.WORK)
        val personal = activity(ActivityType.PERSONAL)
        val vm = create(); await { vm.state.value.activities.size == 2 }
        val first = vm.state.value.drafts.single().id
        vm.setCategory(first, 2)
        vm.setDefaultDate(today.minusDays(1)); vm.setDefaultActivity(work); vm.setDefaultReimbursable(true)
        vm.addDraft()
        val second = vm.state.value.drafts.last()
        assertEquals(2L, second.categoryId); assertEquals(work, second.activityId)
        assertEquals(today.minusDays(1), second.date); assertTrue(second.reimbursable)
        assertEquals(today, vm.state.value.drafts.first().date)
        assertNull(vm.state.value.drafts.first().activityId)
        vm.setDate(second.id, today.minusDays(2)); vm.setActivity(second.id, personal); vm.setReimbursable(second.id, false)
        vm.addDraft()
        val third = vm.state.value.drafts.last()
        assertEquals(work, third.activityId); assertTrue(third.reimbursable)
        assertEquals(today.minusDays(1), third.date)
        vm.setAmount(first, "1"); vm.setAmount(second.id, "2"); vm.setAmount(third.id, "3")
        vm.saveAll(); await { vm.state.value.saved != null }
        assertEquals(personal, rows().single { it.amountMinor == 200L }.activityId)
        assertEquals(today.minusDays(2), rows().single { it.amountMinor == 200L }.date)
        assertFalse(rows().single { it.amountMinor == 200L }.reimbursable)
    }

    @Test fun defaultReimbursableIsOnlyAvailableForWorkAndResetsOnActivityChange() {
        val work = activity(ActivityType.WORK); val personal = activity(ActivityType.PERSONAL)
        val vm = create(); await { vm.state.value.activities.size == 2 }
        vm.setDefaultReimbursable(true); assertFalse(vm.state.value.defaults.reimbursable)
        vm.setDefaultActivity(work); vm.setDefaultReimbursable(true); assertTrue(vm.state.value.defaults.reimbursable)
        vm.setDefaultActivity(personal); assertFalse(vm.state.value.defaultIsWork)
        vm.setDefaultReimbursable(true); vm.addDraft(); assertFalse(vm.state.value.drafts.last().reimbursable)
        vm.setDefaultActivity(null); assertFalse(vm.state.value.defaults.reimbursable)
    }

    @Test fun invalidDraftBlocksWholeBatchAndCanBeCorrectedWithoutLosingOtherRows() {
        val vm = create(); val first = vm.state.value.drafts.single().id
        vm.setAmount(first, "12.34"); vm.addDraft(); val second = vm.state.value.drafts.last().id
        vm.setAmount(second, "1.234"); vm.saveAll()
        assertEquals(setOf(second), vm.state.value.invalidAmountIds); assertTrue(rows().isEmpty())
        assertEquals(1234L, vm.state.value.totalMinor)
        vm.setAmount(second, "0.01"); vm.saveAll(); await { vm.state.value.saved != null }
        assertEquals(2, rows().size); assertEquals(1235L, vm.state.value.saved!!.totalMinor)
    }

    @Test fun staleActivityCausesZeroWritesAndRetrySavesWholeBatch() {
        val work = activity(ActivityType.WORK)
        val vm = create(); await { vm.state.value.activities.size == 1 }
        val first = vm.state.value.drafts.single().id
        vm.setAmount(first, "10"); vm.addDraft(); val second = vm.state.value.drafts.last().id
        vm.setAmount(second, "20"); vm.setActivity(second, work)
        runBlocking { repository.deleteActivity(work) }
        vm.saveAll(); await { vm.state.value.saveFailed }
        assertTrue(rows().isEmpty()); assertEquals(2, vm.state.value.drafts.size)
        assertFalse(vm.state.value.isSaving)
        vm.setActivity(second, null); vm.saveAll(); await { vm.state.value.saved != null }
        assertEquals(2, rows().size)
    }

    @Test fun mixedTypesUseCorrectCategoriesAndSharedAnalysisExcludesReimbursementFromIncome() {
        val vm = create(); val first = vm.state.value.drafts.single().id
        vm.setAmount(first, "12.34"); vm.setReimbursable(first, true)
        vm.addDraft(); val income = vm.state.value.drafts.last().id
        vm.setType(income, TransactionType.INCOME); vm.setCategory(income, 13); vm.setAmount(income, "88.50")
        vm.setReimbursable(income, true); assertFalse(vm.state.value.drafts.last().reimbursable)
        vm.addDraft(); val refund = vm.state.value.drafts.last().id
        assertEquals(13L, vm.state.value.drafts.last().categoryId)
        vm.setType(refund, TransactionType.REIMBURSEMENT); vm.setAmount(refund, "60.01")
        assertEquals("报销", vm.state.value.selectedCategory(vm.state.value.drafts.last())!!.name)
        vm.saveAll(); await { vm.state.value.saved != null }
        val summary = runBlocking { FinancialAnalysis(repository).getPeriodSummary(today, today) }
        assertEquals(8850L, summary.income.ordinaryMinor); assertEquals(6001L, summary.income.reimbursementMinor)
        assertEquals(1234L, summary.expense.totalMinor); assertEquals(0L, summary.expense.dailyMinor)
        assertEquals(16085L, vm.state.value.saved!!.totalMinor)
    }

    @Test fun totalOverflowBlocksSavingUntilAmountsFit() {
        val vm = create(); val first = vm.state.value.drafts.single().id
        vm.setAmount(first, "92233720368547758.07"); vm.addDraft(); val second = vm.state.value.drafts.last().id
        vm.setAmount(second, "0.01"); assertNull(vm.state.value.totalMinor)
        vm.saveAll(); assertTrue(rows().isEmpty()); assertNull(vm.state.value.saved)
        vm.setAmount(first, "1.01"); vm.saveAll(); await { vm.state.value.saved != null }
        assertEquals(102L, vm.state.value.saved!!.totalMinor)
    }

    @Test fun deletingDraftsRecalculatesTotalAndEmptyBatchCannotSave() {
        val vm = create(); val first = vm.state.value.drafts.single().id
        vm.setAmount(first, "5"); vm.addDraft(); val second = vm.state.value.drafts.last().id
        vm.setAmount(second, "6"); assertEquals(1100L, vm.state.value.totalMinor)
        vm.removeDraft(first); assertEquals(600L, vm.state.value.totalMinor)
        vm.removeDraft(second); vm.saveAll(); assertNull(vm.state.value.saved); assertTrue(rows().isEmpty())
        vm.addDraft(); assertEquals(1, vm.state.value.drafts.size)
    }

    @Test fun draftBundlesSurviveParcelRoundTripAndCompletionRestoresWithoutResaving() {
        val handle = SavedStateHandle(); val original = create(handle)
        original.setDefaultDate(today.minusDays(2)); original.addDraft()
        val id = original.state.value.drafts.last().id
        original.setType(id, TransactionType.INCOME); original.setCategory(id, 13)
        original.setAmount(id, "88.50"); original.setNote(id, "草稿备注")
        original.setAmount(original.state.value.drafts.first().id, "12.34")
        // Simulate a SavedState Bundle travelling through the platform parcel boundary.
        val parcel = Parcel.obtain()
        val restored: Bundle
        try {
            parcel.writeBundle(Bundle().apply { handle.keys().forEach { key ->
                when (val value = handle.get<Any?>(key)) {
                    is Long -> putLong(key, value)
                    is Boolean -> putBoolean(key, value)
                    is ArrayList<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putParcelableArrayList(key, value as ArrayList<Bundle>)
                    }
                }
            } })
            parcel.setDataPosition(0)
            restored = requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally { parcel.recycle() }
        val restoredHandle = SavedStateHandle(restored.keySet().associateWith { key ->
            when (key) {
                "drafts" -> restored.getParcelableArrayList("drafts", Bundle::class.java)
                "defaultReimbursable" -> restored.getBoolean(key)
                else -> restored.getLong(key)
            }
        })
        stores.last().clear()
        val recreated = create(restoredHandle)
        assertEquals(original.state.value.drafts, recreated.state.value.drafts)
        assertEquals(original.state.value.defaults, recreated.state.value.defaults)
        recreated.saveAll(); await { recreated.state.value.saved != null }
        stores.last().clear()
        val completed = create(restoredHandle); assertEquals(recreated.state.value.saved, completed.state.value.saved)
        completed.saveAll(); completed.addDraft(); assertEquals(2, rows().size)
    }
}
