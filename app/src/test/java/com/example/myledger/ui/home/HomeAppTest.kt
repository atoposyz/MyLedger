package com.example.myledger.ui.home

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import android.graphics.Canvas
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.local.entity.*
import java.time.LocalDate
import java.io.File
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
class HomeAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val marker = "Stage8-${UUID.randomUUID()}"
    private val today = LocalDate.now()
    private val repository get() = (compose.activity.application as LedgerApplication).repository
    @After fun cleanOnlyOwnData() = runBlocking {
        repository.observeTransactions().first().filter { it.note?.startsWith(marker) == true }
            .forEach { repository.deleteTransaction(it.id) }
        repository.observeActivities().first().filter { it.note?.startsWith(marker) == true }
            .forEach { repository.deleteActivityIfUnused(it.id) }
    }
    private fun awaitEnabled(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any {
            !it.config.contains(SemanticsProperties.Disabled)
        } }
    }
    private fun awaitHome(balance: String) {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_list").fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithTag("transaction_form").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("batch_list").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("home_list").performScrollToIndex(0)
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("home_balance") and hasText(balance)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("首页") and hasClickAction()).assertIsSelected()
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun entry(mode: String, tag: String) {
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText(mode).performClick(); awaitEnabled(tag)
    }
    private fun batch(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun ownRows() = runBlocking { repository.observeTransactions().first().filter { it.note?.startsWith(marker) == true } }

    @Test fun monthlyTotalsRefreshAfterRecentEditActivityRenameAndConfirmedDelete() {
        val work = runBlocking { repository.addActivity(ActivityEntity(name = "ISCA 2027", type = ActivityType.WORK, note = marker)) }
        val personal = runBlocking { repository.addActivity(ActivityEntity(name = "上海演唱会", type = ActivityType.PERSONAL, note = marker)) }
        fun seed(type: TransactionType, amount: Long, suffix: String, activity: Long? = null, reimbursable: Boolean = false,
            date: LocalDate = today) = runBlocking {
            repository.addTransaction(TransactionEntity(type = type, amountMinor = amount,
                categoryId = when (type) { TransactionType.EXPENSE -> 1; TransactionType.INCOME -> 13; TransactionType.REIMBURSEMENT -> 16 },
                activityId = activity, reimbursable = reimbursable, date = date, note = "$marker-$suffix"))
        }
        seed(TransactionType.INCOME, 100_000, "income")
        seed(TransactionType.REIMBURSEMENT, 5_000, "reimbursement")
        seed(TransactionType.EXPENSE, 2_000, "personal", personal)
        seed(TransactionType.EXPENSE, 4_000, "work", work, true)
        seed(TransactionType.EXPENSE, 3_000, "reimbursable", reimbursable = true)
        seed(TransactionType.EXPENSE, 99_900, "previous", date = today.withDayOfMonth(1).minusDays(1))
        val daily = seed(TransactionType.EXPENSE, 1234, "daily")
        awaitHome("¥947.66")
        compose.onNodeWithTag("home_ordinary_income").assertTextContains("¥1000.00")
        compose.onNodeWithTag("home_reimbursement").assertTextContains("¥50.00")
        compose.onNodeWithTag("home_daily_expense").assertTextContains("¥12.34")
        compose.onNodeWithTag("home_all_expense").assertTextContains("¥102.34")
        screenshot("home-populated-light-summary")
        reveal("home_activity_$work").onChildren().filter(hasText("¥40.00")).assertCountEquals(1)
        screenshot("home-populated-light-activities")
        reveal("home_record_$daily").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("transaction_amount").performTextReplacement("18.01")
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        awaitHome("¥941.99")
        compose.onNodeWithTag("home_daily_expense").assertTextContains("¥18.01")
        compose.onNodeWithTag("home_all_expense").assertTextContains("¥108.01")
        reveal("home_activities").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("activities_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("activities_list").performScrollToNode(hasTestTag("activity_$work"))
        compose.onNodeWithTag("activity_$work").performClick(); awaitEnabled("activity_save")
        compose.onNodeWithTag("activity_name").performTextReplacement("ISCA 2027（更新）")
        compose.onNodeWithTag("activity_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("activity_form").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("返回").performClick(); awaitHome("¥941.99")
        reveal("home_activity_$work").onChildren().filter(hasText("ISCA 2027（更新）")).assertCountEquals(1)
        reveal("home_record_$daily").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("transaction_delete").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_delete_confirm").performClick()
        awaitHome("¥960.00")
        compose.onNodeWithTag("home_daily_expense").assertTextContains("¥0.00")
        assertNull(runBlocking { repository.getTransaction(daily) }); assertEquals(6, ownRows().size)
    }

    @Test fun emptyHomeShortcutsAndSingleSaveReturnToHomeAndSurviveRecreation() {
        awaitHome("¥0.00")
        compose.onNodeWithText("本月暂无账目，点击右下角按钮开始记账。").assertExists()
        reveal("home_records").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records_loading").fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasText("明细") and hasClickAction()).assertIsSelected()
        compose.onNode(hasText("首页") and hasClickAction()).performClick(); awaitHome("¥0.00")
        reveal("home_activities").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("activities_list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("activities_list").assertExists()
        compose.onNodeWithContentDescription("返回").performClick(); awaitHome("¥0.00")
        entry("记一笔", "transaction_save")
        compose.onNodeWithTag("transaction_amount").performTextInput("12.34")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(marker)
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick(); awaitHome("−¥12.34")
        val row = ownRows().single()
        reveal("home_record_${row.id}").assertTextContains("−¥12.34")
        compose.activityRule.scenario.recreate(); awaitHome("−¥12.34")
        assertEquals(row, ownRows().single())
    }

    @Test fun batchSaveUpdatesHomeAndRecentTypeChangeSeparatesReimbursementFromOrdinaryIncome() {
        awaitHome("¥0.00"); entry("记多笔", "batch_default_activity")
        batch("batch_amount_1").performTextInput("12.34")
        batch("batch_note_1").performTextInput("$marker-1")
        batch("batch_add").performClick()
        batch("batch_amount_2").performTextInput("37.66")
        batch("batch_note_2").performTextInput("$marker-2")
        batch("batch_save").performClick(); awaitHome("−¥50.00")
        val rows = ownRows(); assertEquals(2, rows.size)
        val id = rows.single { it.note == "$marker-2" }.id
        reveal("home_record_$id").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("type_REIMBURSEMENT").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick(); awaitHome("¥25.32")
        compose.onNodeWithTag("home_reimbursement").assertTextContains("¥37.66")
        compose.onNodeWithTag("home_ordinary_income").assertTextContains("¥0.00")
        compose.onNodeWithTag("home_all_expense").assertTextContains("¥12.34")
        assertEquals(TransactionType.REIMBURSEMENT, runBlocking { repository.getTransaction(id) }!!.type)
        assertEquals(2, ownRows().size)
    }
    private fun screenshot(name: String) {
        val file = File("build/stage8-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
