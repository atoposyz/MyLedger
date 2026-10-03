package com.example.myledger.ui.transaction

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.local.entity.TransactionType
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
class TransactionAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val marker = "Stage4-test-${UUID.randomUUID()}"
    private val date = LocalDate.now()
    private val repository get() = (compose.activity.application as LedgerApplication).repository

    @After fun removeOnlyRecordsCreatedByThisTest() = runBlocking {
        repository.getTransactions(date, date).filter { it.note == marker }.forEach { repository.deleteTransaction(it.id) }
    }

    private fun openForm(tab: String) {
        compose.onNode(hasText(tab) and hasClickAction()).performClick()
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("记一笔").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_save").fetchSemanticsNodes().any {
            !it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)
        } }
    }

    private fun saveAndWaitForReturn() {
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun realFormSavesIntoRoomReturnsToOriginalTabAndShowsReceipt() {
        openForm("统计")
        compose.onNodeWithTag("transaction_amount").performTextInput("12.34")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(marker)
        saveAndWaitForReturn()
        compose.onNode(hasText("统计") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("已保存：支出 ¥12.34 · 餐饮 · $date").assertIsDisplayed()
        val rows = runBlocking { repository.getTransactions(date, date) }.filter { it.note == marker }
        assertEquals(1, rows.size)
        assertEquals(1234L, rows.single().amountMinor)
        assertEquals(TransactionType.EXPENSE, rows.single().type)
        assertEquals(date, rows.single().date)
    }

    @Test fun invalidAmountStaysOnFormAndActivityRecreationPreservesDraft() {
        openForm("明细")
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        compose.onNodeWithText("请输入大于零、最多两位小数的有效金额").assertIsDisplayed()
        compose.onNodeWithTag("type_INCOME").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_amount").performTextInput("88.50")
        compose.onNodeWithTag("transaction_category").performClick()
        compose.onNodeWithText("项目酬金").performClick()
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(marker)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("transaction_amount").performScrollTo().assertTextContains("88.50")
        compose.onNodeWithTag("transaction_category").assertTextContains("分类：项目酬金")
        compose.onNodeWithTag("transaction_note").performScrollTo().assertTextContains(marker)
        saveAndWaitForReturn()
        compose.onNode(hasText("明细") and hasClickAction()).assertIsSelected()
        val saved = runBlocking { repository.getTransactions(date, date) }.single { it.note == marker }
        assertEquals(TransactionType.INCOME, saved.type)
        assertEquals(13L, saved.categoryId)
        assertEquals(8850L, saved.amountMinor)
    }
}
