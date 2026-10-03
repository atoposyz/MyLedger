package com.example.myledger.ui.statistics

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.*
import com.example.myledger.ui.theme.MyLedgerTheme
import java.io.File
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()
    private val month = YearMonth.of(2027, 1)
    private var darkTheme by mutableStateOf(false)
    private fun row(amount: Long, category: Long = 1, activity: Long? = null, previous: Boolean = false) =
        TransactionEntity(type = TransactionType.EXPENSE, amountMinor = amount, categoryId = category, activityId = activity,
            date = (if (previous) month.minusMonths(1) else month).atDay(1))
    private fun render(rows: List<TransactionEntity>, dark: Boolean = false, fontScale: Float = 1f) {
        darkTheme = dark
        val summaries = ExpenseScope.entries.associateWith { FinancialAnalysis.summarizeStatistics(rows, month, it) }
        val names = DefaultCategories.all.associate { it.id to it.name }
        compose.setContent {
            var scope by remember { mutableStateOf(ExpenseScope.DAILY) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyLedgerTheme(darkTheme = darkTheme, dynamicColor = false) { Surface {
                    StatisticsScreen(StatisticsUiState(month, scope, summaries[scope], names, isLoading = false),
                        onScope = { scope = it }, onRetry = {})
                } }
            }
        }
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("statistics_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    private fun select(scope: ExpenseScope) {
        compose.onNodeWithTag("statistics_list").performScrollToIndex(0)
        compose.onNodeWithTag("statistics_scope_${scope.name}").performClick().assertIsSelected()
    }
    @Test fun emptyMonthShowsZeroAndSixEmptyMonthsInBothScopes() {
        render(emptyList())
        compose.onNodeWithTag("statistics_total").assertTextEquals("¥0.00")
        compose.onNodeWithText("本月此口径暂无支出。").assertExists()
        screenshot("statistics-empty-light")
        for (scope in ExpenseScope.entries) {
            select(scope)
            for (offset in 5 downTo 0) reveal("statistics_trend_${month.minusMonths(offset.toLong())}").assertTextContains("¥0.00")
        }
    }
    @Test fun scopeSwitchUpdatesTotalRankPercentageAndTrendAndDarkThemeKeepsValues() {
        render(listOf(row(10000), row(30000, 2), row(20000, activity = 1), row(40000, 2).copy(reimbursable = true),
            row(2000, previous = true), row(5000, activity = 1, previous = true)))
        compose.onNodeWithTag("statistics_total").assertTextEquals("¥400.00")
        reveal("statistics_category_2").assertTextContains("1. 交通").assertTextContains("75.0%")
        screenshot("statistics-daily-light")
        reveal("statistics_trend_2026-12").assertTextContains("¥20.00")
        select(ExpenseScope.ALL)
        compose.onNodeWithTag("statistics_total").assertTextEquals("¥1000.00")
        reveal("statistics_category_2").assertTextContains("1. 交通").assertTextContains("70.0%")
        reveal("statistics_category_1").assertTextContains("2. 餐饮").assertTextContains("30.0%")
        screenshot("statistics-all-light")
        reveal("statistics_trend_2026-12").assertTextContains("¥70.00")
        reveal("statistics_trend_2027-01").assertTextContains("¥1000.00")
        screenshot("statistics-trend-light")
        compose.runOnIdle { darkTheme = true }
        reveal("statistics_trend_2027-01").assertTextContains("¥1000.00")
        screenshot("statistics-trend-dark")
        compose.onNodeWithTag("statistics_list").performScrollToIndex(0)
        compose.onNodeWithTag("statistics_total").assertTextEquals("¥1000.00")
        screenshot("statistics-all-dark")
    }
    @Test fun largeFontAndMaximumAmountRemainReadableAndOverflowAllowsSwitchingBack() {
        render(listOf(row(1234), row(Long.MAX_VALUE, activity = 1), row(Long.MAX_VALUE, previous = true)), true, 1.5f)
        compose.onNodeWithTag("statistics_total").assertTextEquals("¥12.34")
        select(ExpenseScope.ALL)
        compose.onNodeWithTag("statistics_total").assertTextEquals("—")
        compose.onNodeWithText("本月支出超出金额范围，请在明细中检查账目。").assertExists()
        screenshot("statistics-overflow-dark-large")
        reveal("statistics_trend_2026-12").assertTextContains("¥92233720368547758.07")
        screenshot("statistics-maximum-dark-large")
        select(ExpenseScope.DAILY)
        compose.onNodeWithTag("statistics_total").assertTextEquals("¥12.34")
        reveal("statistics_category_1").assertTextContains("100.0%")
    }
    private fun screenshot(name: String) {
        val file = File("build/stage9-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
