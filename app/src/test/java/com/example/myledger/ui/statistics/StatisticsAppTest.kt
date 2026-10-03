package com.example.myledger.ui.statistics

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.local.entity.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlinx.coroutines.flow.first
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
class StatisticsAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val marker = "Stage9-${UUID.randomUUID()}"
    private val today = LocalDate.now()
    private val repository get() = (compose.activity.application as LedgerApplication).repository
    @After fun cleanOnlyOwnData() = runBlocking {
        repository.observeTransactions().first().filter { it.note?.startsWith(marker) == true }.forEach { repository.deleteTransaction(it.id) }
        repository.observeActivities().first().filter { it.note?.startsWith(marker) == true }.forEach { repository.deleteActivityIfUnused(it.id) }
    }
    private fun awaitEnabled(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
    }
    private fun statistics(total: String) {
        compose.onNode(hasText("统计") and hasClickAction()).performClick()
        awaitTotal(total)
    }
    private fun awaitTotal(total: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("statistics_list").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("transaction_form").fetchSemanticsNodes().isEmpty() && compose.onAllNodesWithTag("batch_list").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("statistics_list").performScrollToIndex(0)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("statistics_total") and hasText(total)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("统计") and hasClickAction()).assertIsSelected()
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasTestTag(tag)); return compose.onNodeWithTag(tag)
    }
    private fun ownRows() = runBlocking { repository.observeTransactions().first().filter { it.note?.startsWith(marker) == true } }
    private fun seed(amount: Long, category: Long = 1, activity: Long? = null, reimbursable: Boolean = false,
        date: LocalDate = today, type: TransactionType = TransactionType.EXPENSE) = runBlocking {
        repository.addTransaction(TransactionEntity(type = type, amountMinor = amount, categoryId = category,
            activityId = activity, reimbursable = reimbursable, date = date, note = marker))
    }

    @Test fun scopeMatchesHomePersistsAcrossTabsAndRecreationAndRecordEditDeleteRefreshStatistics() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "出差", type = ActivityType.WORK, note = marker)) }
        val dining = seed(10000); val transport = seed(30000, 2)
        seed(20000, activity = activity); seed(40000, 2, reimbursable = true)
        seed(2000, date = today.minusMonths(1)); seed(5000, activity = activity, date = today.minusMonths(1))
        seed(1000000, 12, type = TransactionType.INCOME); seed(5000, 16, type = TransactionType.REIMBURSEMENT)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("home_daily_expense") and hasText("¥400.00")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("home_all_expense").assertTextContains("¥1000.00")
        statistics("¥400.00")
        reveal("statistics_category_2").assertTextContains("75.0%")
        reveal("statistics_trend_${YearMonth.from(today.minusMonths(1))}").assertTextContains("¥20.00")
        compose.onNodeWithTag("statistics_list").performScrollToIndex(0)
        compose.onNodeWithTag("statistics_scope_ALL").performClick(); awaitTotal("¥1000.00")
        reveal("statistics_category_2").assertTextContains("70.0%")
        screenshot("statistics-app-short-all")
        reveal("statistics_trend_${YearMonth.from(today.minusMonths(1))}").assertTextContains("¥70.00")
        compose.activityRule.scenario.recreate(); awaitTotal("¥1000.00")
        compose.onNodeWithTag("statistics_scope_ALL").assertIsSelected()
        compose.onNode(hasText("首页") and hasClickAction()).performClick(); statistics("¥1000.00")
        compose.onNodeWithTag("statistics_scope_ALL").assertIsSelected()
        compose.onNode(hasText("明细") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_$dining"))
        compose.onNodeWithTag("record_$dining").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("transaction_amount").performTextReplacement("150")
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_form").fetchSemanticsNodes().isEmpty() }
        statistics("¥1050.00")
        compose.onNode(hasText("明细") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_$transport"))
        compose.onNodeWithTag("record_$transport").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("transaction_delete").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_delete_confirm").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_form").fetchSemanticsNodes().isEmpty() }
        statistics("¥750.00")
        assertEquals(15000L, runBlocking { repository.getTransaction(dining) }!!.amountMinor)
        assertNull(runBlocking { repository.getTransaction(transport) })
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h600dp-night")
    fun singleAndBatchSaveReturnToSelectedStatisticsAndImmediatelyUpdateCurrentTrend() {
        statistics("¥0.00")
        compose.onNodeWithTag("statistics_scope_ALL").performClick(); awaitTotal("¥0.00")
        compose.onNodeWithContentDescription("记账").performClick(); compose.onNodeWithText("记一笔").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("transaction_amount").performTextInput("12.34")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput("$marker-single")
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick(); awaitTotal("¥12.34")
        compose.onNodeWithTag("statistics_scope_ALL").assertIsSelected()
        compose.onNodeWithContentDescription("记账").performClick(); compose.onNodeWithText("记多笔").performClick(); awaitEnabled("batch_default_activity")
        fun batch(tag: String): SemanticsNodeInteraction {
            compose.onNodeWithTag("batch_list").performScrollToNode(hasTestTag(tag)); return compose.onNodeWithTag(tag)
        }
        batch("batch_amount_1").performTextInput("37.66"); batch("batch_note_1").performTextInput("$marker-batch")
        batch("batch_save").performClick(); awaitTotal("¥50.00")
        compose.onNodeWithTag("statistics_scope_ALL").assertIsSelected()
        reveal("statistics_category_1").assertTextContains("¥50.00").assertTextContains("100.0%")
        screenshot("statistics-app-short-dark")
        reveal("statistics_trend_${YearMonth.from(today)}").assertTextContains("¥50.00")
        assertEquals(2, ownRows().size); assertEquals(5000L, ownRows().sumOf { it.amountMinor })
    }
    private fun screenshot(name: String) {
        val file = File("build/stage9-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
