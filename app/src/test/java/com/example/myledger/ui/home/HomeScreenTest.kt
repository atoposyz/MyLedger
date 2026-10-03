package com.example.myledger.ui.home

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.data.local.entity.*
import com.example.myledger.ui.records.RecordItem
import com.example.myledger.ui.theme.MyLedgerTheme
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
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
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 10, 3)
    private var opened: Long? = null
    private var records = 0
    private var activities = 0
    private fun row(id: Long, amount: Long, activity: Long? = null) = TransactionEntity(id = id,
        type = TransactionType.EXPENSE, amountMinor = amount, categoryId = 1, activityId = activity,
        date = day, note = if (id == 1L) "长备注用于检查两行截断。".repeat(100) else "早餐")
    private fun render(rows: List<TransactionEntity>, dark: Boolean, fontScale: Float = 1f) {
        val activity = ActivityEntity(id = 1, name = "上海演唱会与朋友共同出行的长活动名称", type = ActivityType.PERSONAL)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyLedgerTheme(darkTheme = dark, dynamicColor = false) { Surface {
                    HomeScreen(HomeUiState(month = YearMonth.from(day), summary = FinancialAnalysis.summarizeMonth(rows, YearMonth.from(day)),
                        activityById = mapOf(1L to activity), recentRecords = rows.map { RecordItem(it, "餐饮", if (it.activityId == 1L) activity.name else null) },
                        isLoading = false), onOpenRecord = { opened = it }, onRecords = { records++ }, onActivities = { activities++ }, onRetry = {})
                } }
            }
        }
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        compose.onNodeWithTag("home_list").performScrollToNode(hasTestTag(tag))
        return compose.onNodeWithTag(tag)
    }
    @Test fun emptyHomeShowsRealZerosAndBothNavigationActions() {
        render(emptyList(), false)
        compose.onNodeWithTag("home_balance").assertTextEquals("¥0.00")
        compose.onNodeWithTag("home_all_expense").assertTextContains("¥0.00")
        screenshot("home-empty-light")
        reveal("home_activities").performClick(); assertEquals(1, activities)
        reveal("home_records").performClick(); assertEquals(1, records)
        compose.onNodeWithText("暂无账目，保存后会在这里显示。").assertExists()
    }
    @Test fun largeFontDarkModeKeepsLargestSupportedNegativeBalanceAndRecentRecordAccessible() {
        render(listOf(row(1, Long.MAX_VALUE, 1)), true, 1.5f)
        compose.onNodeWithTag("home_balance").assertTextEquals("−¥92233720368547758.07")
        screenshot("home-dark-large-summary")
        reveal("home_activity_1").assertExists(); screenshot("home-dark-large-activity")
        reveal("home_record_1").performClick(); assertEquals(1L, opened)
        screenshot("home-dark-large-recent")
    }
    @Test fun overflowingSummaryShowsUnavailableWithoutBlockingRecentEditor() {
        render(listOf(row(1, Long.MAX_VALUE, 1), row(2, 1, 1)), false)
        compose.onNodeWithTag("home_balance").assertTextEquals("—")
        compose.onNodeWithTag("home_all_expense").assertTextContains("—")
        compose.onNodeWithText("本月合计超出金额范围，请在明细中检查账目。").assertExists()
        screenshot("home-overflow-light")
        reveal("home_record_2").performClick(); assertEquals(2L, opened)
    }
    private fun screenshot(name: String) {
        val file = File("build/stage8-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
