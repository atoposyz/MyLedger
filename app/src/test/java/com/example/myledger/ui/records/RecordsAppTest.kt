package com.example.myledger.ui.records

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RecordsAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val today = LocalDate.now()
    private val marker = "Stage6-test-${UUID.randomUUID()}"
    private val repository get() = (compose.activity.application as LedgerApplication).repository
    @After fun cleanOnlyOwnRecords() = runBlocking {
        repository.getTransactions(today.minusDays(40), today.plusDays(40)).filter { it.note?.startsWith(marker) == true }
            .forEach { repository.deleteTransaction(it.id) }
    }
    private fun seed(date: LocalDate = today, suffix: String = "") = runBlocking {
        repository.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 1234,
            categoryId = 1, date = date, note = marker + suffix))
    }
    private fun records() {
        compose.onNode(hasText("明细") and hasClickAction()).performClick()
        awaitRecords()
    }
    private fun awaitRecords() {
        compose.onNodeWithTag("records_list").performScrollToIndex(0)
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("records_filter").assertExists()
    }
    private fun revealRecord(id: Long): SemanticsNodeInteraction {
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_$id"))
        return compose.onNodeWithTag("record_$id")
    }
    private fun awaitEditor() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_save").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
    }
    private fun chooseDay(date: LocalDate) {
        compose.onNodeWithText(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日EEEE", Locale.SIMPLIFIED_CHINESE)), substring = true).performClick()
        compose.onNodeWithText("确定").performClick()
    }
    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h891dp")
    fun newlyAddedRecordAppearsThenEditingDateAndAmountRegroupsWithoutDuplicating() = exerciseEditSave(accessibility = false)

    @Test fun shortScreenEditorSupportsAccessibilitySaveAfterDateSelection() = exerciseEditSave(accessibility = true)

    @Test fun shortScreenEditorSupportsTouchSaveAfterDateSelection() = exerciseEditSave(accessibility = false)

    private fun exerciseEditSave(accessibility: Boolean) {
        records()
        compose.onNodeWithContentDescription("记账").performClick(); compose.onNodeWithText("记一笔").performClick()
        awaitEditor()
        compose.onNodeWithTag("transaction_amount").performTextInput("12.34")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(marker)
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        awaitReturn()
        val id = runBlocking { repository.getTransactions(today, today).single { it.note == marker }.id }
        revealRecord(id).assertTextContains("−¥12.34").performClick(); awaitEditor()
        compose.onNodeWithText("查看与编辑").assertIsDisplayed()
        compose.onNodeWithTag("transaction_amount").performScrollTo().assertTextContains("12.34").performTextReplacement("18.01")
        val changedDate = today.withDayOfMonth(if (today.dayOfMonth == 10) 11 else 10)
        compose.onNodeWithTag("transaction_date").performScrollTo().performClick(); chooseDay(changedDate)
        compose.onNodeWithTag("transaction_form").performTouchInput { swipeUp() }
        val save = compose.onNodeWithTag("transaction_save").assertIsDisplayed().assertIsEnabled()
        if (accessibility) save.performSemanticsAction(SemanticsActions.OnClick) { it() } else save.performClick()
        awaitReturn()
        revealRecord(id).assertTextContains("−¥18.01")
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("records_day_$changedDate"))
        compose.onNode(hasText("支出 ¥18.01") and hasAnyAncestor(hasTestTag("records_day_$changedDate"))).assertExists()
        val saved = runBlocking { repository.getTransaction(id) }!!
        assertEquals(changedDate, saved.date); assertEquals(1801L, saved.amountMinor)
        assertEquals(1, runBlocking { repository.getTransactions(today.minusDays(40), today.plusDays(40)).count { it.note == marker } })
    }
    private fun awaitReturn() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun dateRangeIncludesEndpointsPersistsAcrossRecreationAndClears() {
        val firstDate = today.withDayOfMonth(1); val secondDate = today.withDayOfMonth(2)
        val first = seed(firstDate, "-first"); val second = seed(secondDate, "-second")
        val third = seed(today.withDayOfMonth(3), "-third")
        records()
        compose.onNodeWithTag("records_filter").performClick()
        compose.onNodeWithTag("records_range_end").performClick(); chooseDay(secondDate)
        compose.onNodeWithTag("records_range_apply").performClick(); awaitRecords()
        revealRecord(first).assertExists(); revealRecord(second).assertExists()
        assertThrows(AssertionError::class.java) { revealRecord(third) }
        compose.activityRule.scenario.recreate(); awaitRecords()
        compose.onNodeWithTag("records_list").performScrollToIndex(0)
        compose.onNodeWithTag("records_filter").assertTextContains("$firstDate 至 $secondDate")
        revealRecord(first).assertExists(); revealRecord(second).assertExists()
        compose.onNodeWithTag("records_list").performScrollToIndex(0)
        compose.onNodeWithTag("records_clear_filter").performClick(); awaitRecords()
        revealRecord(third).assertExists()
    }
    @Test fun deleteNeedsConfirmationCancelKeepsRecordAndConfirmRemovesOnlySelectedRow() {
        val id = seed(); val other = seed(suffix = "-other"); records()
        revealRecord(id).performClick(); awaitEditor()
        compose.onNodeWithTag("transaction_delete").performScrollTo().performClick()
        compose.onNodeWithText("确认删除账目？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        assertNotNull(runBlocking { repository.getTransaction(id) })
        compose.onNodeWithTag("transaction_delete").performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("确认删除账目？").assertIsDisplayed()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_delete_confirm").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
        compose.onNodeWithTag("transaction_delete_confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("明细") and hasClickAction()).assertIsSelected()
        assertNull(runBlocking { repository.getTransaction(id) }); assertNotNull(runBlocking { repository.getTransaction(other) })
        revealRecord(other).assertExists()
        assertThrows(AssertionError::class.java) { revealRecord(id) }
    }
}
