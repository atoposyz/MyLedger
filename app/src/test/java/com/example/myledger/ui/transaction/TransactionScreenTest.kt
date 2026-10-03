package com.example.myledger.ui.transaction

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.*
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.ui.theme.MyLedgerTheme
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionScreenTest {
    @get:Rule val compose = createComposeRule()
    private val saves = AtomicInteger()

    private fun render(dark: Boolean = false, amountError: Boolean = false) {
        compose.setContent {
            var form by remember { mutableStateOf(TransactionForm(date = LocalDate.of(2026, 10, 3))) }
            MyLedgerTheme(darkTheme = dark, dynamicColor = false) {
                Surface {
                TransactionScreen(
                    TransactionUiState(form, allCategories = DefaultCategories.all,
                        activities = listOf(ActivityEntity(1, "项目出差", ActivityType.WORK)),
                        isLoading = false, amountError = amountError),
                    onType = { form = form.copy(type = it, categoryId = null, reimbursable = false) },
                    onAmount = { form = form.copy(amount = it) }, onCategory = { form = form.copy(categoryId = it) },
                    onActivity = { form = form.copy(activityId = it) }, onReimbursable = { form = form.copy(reimbursable = it) },
                    onDate = { form = form.copy(date = it) }, onNote = { form = form.copy(note = it) },
                    onSave = { saves.incrementAndGet() }, onRetry = {},
                )
                }
            }
        }
    }

    @Test fun expenseFormSupportsAmountCategoryActivityFlagDateNoteAndSave() {
        render()
        compose.onNodeWithTag("transaction_amount").performTextInput("12.34")
        compose.onNodeWithTag("transaction_category").performClick()
        compose.onNodeWithText("交通").performClick()
        compose.onNodeWithTag("transaction_category").assertTextContains("分类：交通")
        compose.onNodeWithTag("transaction_activity").performClick()
        compose.onNodeWithText("项目出差").performClick()
        compose.onNodeWithTag("transaction_activity").assertTextContains("活动：项目出差")
        compose.onNodeWithTag("transaction_reimbursable").performClick().assertIsOn()
        compose.onNodeWithTag("transaction_date").performScrollTo().performClick()
        compose.onNodeWithText("2026年10月4日星期日", substring = true).performClick()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithTag("transaction_date").assertTextContains("日期：2026-10-04")
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput("打车")
        compose.onNodeWithTag("transaction_save").performScrollTo().performClick()
        assertEquals(1, saves.get())
        screenshot("form-light")
    }

    @Test fun incomeAndReimbursementShowTheirOwnCategoriesAndHideReimbursableSwitch() {
        render(dark = true)
        compose.onNodeWithTag("type_INCOME").performClick().assertIsSelected()
        compose.onNodeWithTag("transaction_category").assertTextContains("分类：固定工资")
        compose.onNodeWithTag("transaction_reimbursable").assertDoesNotExist()
        compose.onNodeWithTag("transaction_category").performClick()
        compose.onNodeWithText("项目酬金").performClick()
        compose.onNodeWithTag("transaction_category").assertTextContains("分类：项目酬金")
        compose.onNodeWithTag("type_REIMBURSEMENT").performClick().assertIsSelected()
        compose.onNodeWithTag("transaction_category").assertTextContains("分类：报销")
        compose.onNodeWithTag("transaction_reimbursable").assertDoesNotExist()
        screenshot("form-dark")
    }

    @Test fun validationErrorIsVisibleAndDateDialogCanBeCancelled() {
        render(amountError = true)
        compose.onNodeWithText("请输入大于零、最多两位小数的有效金额").assertIsDisplayed()
        compose.onNodeWithTag("transaction_date").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithTag("transaction_date").assertTextContains("日期：2026-10-03")
    }

    private fun screenshot(name: String) {
        val file = File("build/stage4-ui/$name.png")
        file.parentFile?.mkdirs()
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
