package com.example.myledger.ui.records

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
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
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
class RecordsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 10, 3)
    private var opened: Long? = null
    private var applied: RecordDateRange? = null
    private var darkTheme by mutableStateOf(false)
    private fun row(id: Long, type: TransactionType, amount: Long, date: LocalDate = day) =
        TransactionEntity(id = id, type = type, amountMinor = amount,
            categoryId = when (type) { TransactionType.EXPENSE -> 1; TransactionType.INCOME -> 13; TransactionType.REIMBURSEMENT -> 16 },
            date = date, note = if (id == 1L) "午饭" else null)
    private fun render(dark: Boolean, overflow: Boolean = false) {
        darkTheme = dark
        val rows = if (overflow) listOf(row(1, TransactionType.EXPENSE, Long.MAX_VALUE).copy(note = "长备注".repeat(100)),
            row(2, TransactionType.EXPENSE, 1)) else listOf(row(1, TransactionType.EXPENSE, 1234),
            row(2, TransactionType.INCOME, 8850), row(3, TransactionType.REIMBURSEMENT, 6001),
            row(4, TransactionType.EXPENSE, 1, day.minusDays(1)))
        compose.setContent {
            var range by remember { mutableStateOf<RecordDateRange?>(if (overflow) RecordDateRange(day, day.plusDays(2)) else null) }
            MyLedgerTheme(darkTheme = darkTheme, dynamicColor = false) { Surface {
                RecordsScreen(RecordsUiState(range, RecordsGrouping.group(rows, DefaultCategories.all, emptyList()), isLoading = false),
                    onOpen = { opened = it }, onRange = { range = it; applied = it }, onRetry = {})
            } }
        }
    }
    @Test fun quickRangesUseNaturalDaysAndCanToggleBackToAll() {
        render(false)
        val today = LocalDate.now(); val previous = java.time.YearMonth.from(today).minusMonths(1)
        compose.onNodeWithTag("records_quick_0").performClick().assertIsSelected()
        assertEquals(RecordDateRange(today, today), applied)
        compose.onNodeWithTag("records_quick_0").performClick().assertIsNotSelected(); assertNull(applied)
        compose.onNodeWithTag("records_quick_2").performClick().assertIsSelected()
        assertEquals(RecordDateRange(previous.atDay(1), previous.atEndOfMonth()), applied)
    }

    @Test fun showsSignedRowsSeparateIncomeAndReimbursementAndOpensEditor() {
        render(false)
        compose.onNodeWithText("支出 ¥12.34").assertIsDisplayed()
        compose.onNodeWithText("普通收入 ¥88.50 · 报销到账 ¥60.01").assertIsDisplayed()
        compose.onNodeWithText("−¥12.34").assertIsDisplayed()
        compose.onNodeWithText("+¥60.01").assertIsDisplayed()
        compose.onNodeWithTag("record_1").performClick(); assertEquals(1L, opened)
        screenshot("records-light")
        compose.runOnIdle { darkTheme = true }
        compose.onNodeWithText("支出 ¥12.34").assertIsDisplayed()
        screenshot("records-dark")
    }
    @Test fun overflowingDayRemainsEditableAndRangeDialogRejectsReversedDates() {
        render(true, overflow = true)
        compose.onNodeWithText("当天合计超出金额范围，请检查账目").assertIsDisplayed()
        compose.onNodeWithTag("record_1").performClick(); assertEquals(1L, opened)
        screenshot("records-dark-large")
        compose.onNodeWithTag("records_filter").performClick()
        compose.onNodeWithTag("records_range_start").performClick()
        compose.onNodeWithText("2026年10月4日星期日", substring = true).performClick(); compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("records_range_end").performClick()
        compose.onNodeWithText("2026年10月3日星期六", substring = true).performClick(); compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText("结束日期不能早于开始日期").assertIsDisplayed()
        compose.onNodeWithTag("records_range_apply").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick(); assertNull(applied)
        compose.onNodeWithTag("records_filter").performClick()
        compose.onNodeWithTag("records_range_end").performClick()
        compose.onNodeWithText("2026年10月4日星期日", substring = true).performClick(); compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("records_range_apply").performClick()
        assertEquals(RecordDateRange(day, day.plusDays(1)), applied)
        compose.onNodeWithTag("records_clear_filter").performClick()
        compose.onNodeWithText("日期范围：全部").assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        val file = File("build/stage6-ui/$name.png"); file.parentFile?.mkdirs()
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
