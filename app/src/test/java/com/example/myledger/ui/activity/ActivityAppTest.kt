package com.example.myledger.ui.activity

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.data.local.entity.*
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
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
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ActivityAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val marker = "Stage7-test-${UUID.randomUUID()}"
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
    private fun openActivities() {
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        compose.onNodeWithTag("settings_activities").performClick(); awaitList()
        compose.onNodeWithContentDescription("记账").assertDoesNotExist()
    }
    private fun awaitList() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("activities_loading").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("activity_form").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("activities_list").assertExists()
    }
    private fun revealActivity(id: Long) {
        compose.onNodeWithTag("activities_list").performScrollToNode(hasTestTag("activity_$id"))
        compose.onNodeWithTag("activity_$id").performClick(); awaitEnabled("activity_save")
    }
    private fun saveActivity() {
        compose.onNodeWithTag("activity_save").performScrollTo().performClick(); awaitList()
    }
    private fun chooseDate(date: LocalDate) {
        compose.onNodeWithText(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日EEEE", Locale.SIMPLIFIED_CHINESE)), substring = true).performClick()
        compose.onNodeWithText("确定").performClick()
    }
    private fun ownActivity(suffix: String) = runBlocking {
        repository.observeActivities().first().single { it.note == "$marker-$suffix" }
    }
    private fun createActivity(name: String, suffix: String, work: Boolean = false): ActivityEntity {
        compose.onNodeWithTag("activity_add").performClick(); awaitEnabled("activity_save")
        compose.onNodeWithTag("activity_name").performTextInput(name)
        if (work) compose.onNodeWithTag("activity_type_WORK").performClick()
        compose.onNodeWithTag("activity_note").performScrollTo().performTextInput("$marker-$suffix")
        saveActivity(); return ownActivity(suffix)
    }
    private fun entry(mode: String, tag: String) {
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("记账").performClick(); compose.onNodeWithText(mode).performClick()
        awaitEnabled(tag)
        compose.onAllNodes(hasText("已保存", substring = true)).assertCountEquals(0)
    }
    private fun batch(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch_list").performScrollToNode(hasTestTag(tag)); return compose.onNodeWithTag(tag)
    }
    private fun ownRows() = runBlocking { repository.observeTransactions().first().filter { it.note?.startsWith(marker) == true } }
    private fun awaitRecords() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("records_loading").fetchSemanticsNodes().isEmpty() &&
            compose.onAllNodesWithTag("records_list").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun validatesOptionalDatesRestoresDraftAndEditsSameActivityWhileCancelKeepsData() {
        openActivities(); compose.onNodeWithTag("activity_add").performClick(); awaitEnabled("activity_save")
        compose.onNodeWithTag("activity_save").performScrollTo().performClick()
        compose.onNodeWithText("请输入活动名称").assertIsDisplayed()
        compose.onNodeWithTag("activity_name").performTextInput("上海演唱会")
        val start = today.withDayOfMonth(10); val end = today.withDayOfMonth(12)
        compose.onNodeWithTag("activity_start").performScrollTo().performClick(); chooseDate(start)
        compose.onNodeWithTag("activity_end").performScrollTo().performClick(); chooseDate(start.minusDays(1))
        compose.onNodeWithText("结束日期不能早于开始日期").assertExists()
        compose.onNodeWithTag("activity_save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("activity_clear_end").performScrollTo().performClick()
        compose.onNodeWithTag("activity_save").assertIsEnabled()
        compose.onNodeWithTag("activity_end").performScrollTo().performClick(); chooseDate(end)
        compose.onNodeWithTag("activity_note").performScrollTo().performTextInput("$marker-personal")
        compose.activityRule.scenario.recreate(); awaitEnabled("activity_save")
        compose.onNodeWithTag("activity_name").performScrollTo().assertTextContains("上海演唱会")
        compose.onNodeWithTag("activity_start").assertTextContains("开始日期：$start")
        compose.onNodeWithTag("activity_end").assertTextContains("结束日期：$end")
        screenshot("activity-editor-light")
        saveActivity(); val saved = ownActivity("personal")
        assertEquals(start, saved.startDate); assertEquals(end, saved.endDate)
        revealActivity(saved.id); compose.onNodeWithTag("activity_name").performTextReplacement("未保存的名称")
        compose.onNodeWithContentDescription("返回").performClick(); awaitList()
        assertEquals(saved, runBlocking { repository.getActivity(saved.id) })
        revealActivity(saved.id); compose.onNodeWithTag("activity_name").performTextReplacement("上海演唱会（更新）")
        compose.onNodeWithTag("activity_type_WORK").performClick()
        compose.onNodeWithTag("activity_clear_start").performClick(); saveActivity()
        val updated = ownActivity("personal")
        assertEquals(saved.id, updated.id); assertEquals("上海演唱会（更新）", updated.name)
        assertEquals(ActivityType.WORK, updated.type); assertNull(updated.startDate); assertEquals(end, updated.endDate)
    }

    @Test fun createdPersonalAndWorkActivitiesAreUsableInSingleAndBatchEntriesAndRenameRefreshesRecords() {
        openActivities()
        val personal = createActivity("上海演唱会", "personal")
        val work = createActivity("ISCA 2027", "work", work = true)
        screenshot("activities-light")
        entry("记一笔", "transaction_save")
        compose.onNodeWithTag("transaction_amount").performTextInput("12.34")
        compose.onNodeWithTag("transaction_activity").performScrollTo().performClick()
        compose.onNodeWithText(personal.name).performClick()
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput("$marker-single")
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("记账").performClick(); compose.onNodeWithText("记多笔").performClick()
        awaitEnabled("batch_default_activity")
        batch("batch_default_activity").performClick(); compose.onNodeWithText(work.name).performClick()
        batch("batch_default_reimbursable").performClick()
        batch("batch_remove_1").performClick()
        for (id in 2..4) {
            batch("batch_add").performClick()
            batch("batch_amount_$id").performTextInput(when (id) { 2 -> "15"; 3 -> "20"; else -> "30" })
            batch("batch_note_$id").performTextInput("$marker-batch-$id")
        }
        batch("batch_details_4").performClick()
        batch("batch_activity_4").performClick(); compose.onNodeWithText(personal.name).performClick()
        batch("batch_reimbursable_4").performClick()
        compose.onAllNodes(hasText("已保存", substring = true)).assertCountEquals(0)
        batch("batch_save").assertIsDisplayed().assertIsEnabled().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("batch_list").fetchSemanticsNodes().isEmpty() }
        val rows = ownRows()
        assertEquals(4, rows.size); assertEquals(4, rows.map { it.id }.distinct().size)
        assertEquals(2, rows.count { it.activityId == personal.id && !it.reimbursable })
        assertEquals(2, rows.count { it.activityId == work.id && it.reimbursable })
        assertTrue(rows.all { it.date == today })
        val summary = FinancialAnalysis.summarize(rows, today, today)
        assertEquals(7734L, summary.expense.totalMinor); assertEquals(0L, summary.expense.dailyMinor)
        assertEquals(3500L, summary.expense.reimbursableMinor)
        val single = rows.single { it.note == "$marker-single" }
        compose.onNode(hasText("明细") and hasClickAction()).performClick()
        awaitRecords()
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_${single.id}"))
        compose.onNodeWithTag("record_${single.id}").assertTextContains("活动：上海演唱会")
        openActivities(); revealActivity(personal.id)
        compose.onNodeWithTag("activity_name").performTextReplacement("上海演唱会（改名）"); saveActivity()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("明细") and hasClickAction()).performClick()
        awaitRecords()
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_${single.id}"))
        compose.onNodeWithTag("record_${single.id}").assertTextContains("活动：上海演唱会（改名）")
        assertEquals(rows, ownRows())
    }

    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h600dp-night")
    fun referencedDeletionIsBlockedAndUnusedDeletionRequiresConfirmationWhileLedgerSurvives() {
        val activity = runBlocking { repository.addActivity(ActivityEntity(name = "上海演唱会", type = ActivityType.PERSONAL, note = "$marker-delete")) }
        val other = runBlocking { repository.addActivity(ActivityEntity(name = "保留活动", type = ActivityType.WORK, note = "$marker-other")) }
        val id = runBlocking { repository.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 1234,
            categoryId = 1, date = today, activityId = activity, note = "$marker-record")) }
        val original = runBlocking { repository.getTransaction(id) }
        openActivities(); screenshot("activities-dark"); revealActivity(activity)
        screenshot("activity-editor-dark")
        compose.onNodeWithTag("activity_delete").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("此活动仍有关联账目").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("已有 1 条账目关联此活动", substring = true).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("此活动仍有关联账目").assertIsDisplayed()
        awaitEnabled("activity_delete_confirm"); compose.onNodeWithText("知道了").performClick()
        assertEquals(original, runBlocking { repository.getTransaction(id) }); assertNotNull(runBlocking { repository.getActivity(activity) })
        compose.onNodeWithContentDescription("返回").performClick(); awaitList()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("明细") and hasClickAction()).performClick()
        awaitRecords()
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_$id"))
        compose.onNodeWithTag("record_$id").performClick(); awaitEnabled("transaction_save")
        compose.onNodeWithTag("transaction_activity").performScrollTo().performClick()
        compose.onNodeWithText("不关联活动").performClick()
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("transaction_amount").fetchSemanticsNodes().isEmpty() }
        openActivities(); revealActivity(activity)
        compose.onNodeWithTag("activity_delete").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("确认删除活动？").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("取消").performClick(); assertNotNull(runBlocking { repository.getActivity(activity) })
        compose.onNodeWithTag("activity_delete").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("确认删除活动？").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate(); awaitEnabled("activity_delete_confirm")
        compose.onNodeWithTag("activity_delete_confirm").performClick(); awaitList()
        assertNull(runBlocking { repository.getActivity(activity) }); assertNotNull(runBlocking { repository.getActivity(other) })
        val row = runBlocking { repository.getTransaction(id) }!!
        assertEquals(original!!.amountMinor, row.amountMinor); assertNull(row.activityId); assertEquals(original.createdAt, row.createdAt)
    }

    private fun screenshot(name: String) {
        val file = File("build/stage7-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
