package com.example.myledger.ui.batchentry

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.ui.theme.MyLedgerTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BatchEntryScreenTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 3)
    private var current = BatchEntryUiState(defaults = BatchDefaults(date = date),
        drafts = listOf(TransactionDraft(id = 1, date = date, amount = "12.34")),
        categories = DefaultCategories.all, isLoading = false,
        activities = listOf(ActivityEntity(1, "项目出差", ActivityType.WORK), ActivityEntity(2, "旅行", ActivityType.PERSONAL)))
    private fun render(dark: Boolean) {
        compose.setContent {
            var state by remember { mutableStateOf(current) }
            fun update(value: BatchEntryUiState) { state = value; current = value }
            fun draft(id: Long, change: (TransactionDraft) -> TransactionDraft) {
                update(state.copy(drafts = state.drafts.map { if (it.id == id) change(it) else it }))
            }
            MyLedgerTheme(darkTheme = dark, dynamicColor = false) { Surface {
                BatchDailyEntryScreen(state,
                    onDefaultDate = { update(state.copy(defaults = state.defaults.copy(date = it))) },
                    onDefaultActivity = { update(state.copy(defaults = state.defaults.copy(activityId = it, reimbursable = false))) },
                    onDefaultReimbursable = { update(state.copy(defaults = state.defaults.copy(reimbursable = it))) },
                    onAdd = {}, onRemove = { id -> update(state.copy(drafts = state.drafts.filterNot { it.id == id })) },
                    onType = { id, type -> draft(id) { it.copy(type = type, categoryId = null, reimbursable = false) } },
                    onAmount = { id, amount -> draft(id) { it.copy(amount = amount) } },
                    onCategory = { id, category -> draft(id) { it.copy(categoryId = category) } },
                    onActivity = { id, activity -> draft(id) { it.copy(activityId = activity) } },
                    onReimbursable = { id, value -> draft(id) { it.copy(reimbursable = value) } },
                    onDate = { id, date -> draft(id) { it.copy(date = date) } },
                    onNote = { id, note -> draft(id) { it.copy(note = note) } }, onSave = {}, onRetry = {})
            } }
        }
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("batch_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    @Test fun workDefaultsShowSwitchAndRowCanOverrideDateActivityAndReimbursable() {
        render(false)
        compose.onNodeWithTag("batch_default_activity").performClick(); compose.onNodeWithText("项目出差").performClick()
        compose.onNodeWithTag("batch_default_reimbursable").performClick().assertIsOn()
        compose.onNodeWithTag("batch_default_date").performClick()
        compose.onNodeWithText("2026年10月4日星期日").performClick(); compose.onNodeWithText("确定").performClick()
        assertEquals(date.plusDays(1), current.defaults.date)
        assertEquals(date, current.drafts.single().date)
        screenshot("defaults-light")
        reveal("batch_details_1").performClick()
        reveal("batch_date_1").performClick()
        compose.onNodeWithText("2026年10月5日星期一").performClick(); compose.onNodeWithText("确定").performClick()
        reveal("batch_activity_1").performClick(); compose.onNodeWithText("旅行").performClick()
        reveal("batch_reimbursable_1").performClick().assertIsOn()
        assertEquals(date.plusDays(2), current.drafts.single().date)
        assertEquals(2L, current.drafts.single().activityId)
        assertTrue(current.drafts.single().reimbursable)
        reveal("batch_default_activity").performClick(); compose.onNodeWithText("旅行").performClick()
        compose.onNodeWithTag("batch_default_reimbursable").assertDoesNotExist()
    }
    @Test fun mixedTypeSwitchesCategoryAndDarkFormStaysReadable() {
        render(true)
        reveal("batch_type_1_INCOME").performClick()
        reveal("batch_category_1").assertTextContains("分类：固定工资")
        compose.onNodeWithTag("batch_reimbursable_1").assertDoesNotExist()
        reveal("batch_type_1_REIMBURSEMENT").performClick()
        reveal("batch_category_1").assertTextContains("分类：报销")
        reveal("batch_note_1").performTextInput("报销到账")
        screenshot("row-dark")
        reveal("batch_total").assertTextContains("共 1 笔 · 录入合计 ¥12.34")
        screenshot("total-dark")
        reveal("batch_remove_1").performClick()
        compose.onNodeWithTag("batch_save").assertIsNotEnabled()
        compose.onNodeWithText("没有待保存的记录，请添加一笔。").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        val file = File("build/stage5-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
