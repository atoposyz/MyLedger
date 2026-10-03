package com.example.myledger.ui.batchentry

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import java.time.LocalDate
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
class BatchEntryAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val marker = "Stage5-test-${UUID.randomUUID()}"
    private val date = LocalDate.now()
    private val repository get() = (compose.activity.application as LedgerApplication).repository
    @After fun cleanOnlyOwnTestRecords() = runBlocking {
        repository.getTransactions(date.minusDays(2), date.plusDays(2)).filter { it.note?.startsWith(marker) == true }
            .forEach { repository.deleteTransaction(it.id) }
    }
    private fun open() {
        compose.onNode(hasText("统计") and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("记多笔").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("batch_default_activity").fetchSemanticsNodes().any {
                !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
            }
        }
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun ownRows() = runBlocking {
        repository.getTransactions(date.minusDays(2), date.plusDays(2)).filter { it.note?.startsWith(marker) == true }
    }
    private fun save() {
        reveal("batch_save").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("batch_list").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun actualScreenWritesTenIndependentRowsAndReturnsToStatisticsWithExactTotal() {
        open()
        for (index in 1..10) {
            if (index > 1) reveal("batch_add").performClick()
            reveal("batch_amount_$index").performTextInput("$index.01")
            reveal("batch_note_$index").performTextInput("$marker-$index")
        }
        reveal("batch_total").assertTextContains("共 10 笔 · 录入合计 ¥55.10")
        save()
        compose.onNode(hasText("统计") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("已保存 10 笔 · 录入合计 ¥55.10").assertIsDisplayed()
        val saved = ownRows()
        assertEquals(10, saved.size); assertEquals(10, saved.map { it.id }.distinct().size)
        assertEquals(5510L, saved.sumOf { it.amountMinor })
        assertTrue(saved.all { it.date == date })
    }

    @Test fun invalidBatchWritesNothingRestoresAcrossActivityRecreationAndCanRemoveBadDraft() {
        open()
        reveal("batch_amount_1").performTextInput("12.34")
        reveal("batch_category_1").performClick(); compose.onNodeWithText("交通").performClick()
        reveal("batch_note_1").performTextInput(marker)
        reveal("batch_add").performClick()
        reveal("batch_category_2").assertTextContains("分类：交通")
        reveal("batch_amount_2").performTextInput("1.234")
        reveal("batch_save").performClick()
        compose.onNodeWithText("请输入大于零、最多两位小数的有效金额").assertIsDisplayed()
        assertTrue(ownRows().isEmpty())
        compose.activityRule.scenario.recreate()
        reveal("batch_amount_1").assertTextContains("12.34")
        reveal("batch_note_1").assertTextContains(marker)
        reveal("batch_amount_2").assertTextContains("1.234")
        reveal("batch_remove_2").performClick()
        reveal("batch_total").assertTextContains("共 1 笔 · 录入合计 ¥12.34")
        save()
        assertEquals(1234L, ownRows().single().amountMinor)
        assertEquals(2L, ownRows().single().categoryId)
    }
}
