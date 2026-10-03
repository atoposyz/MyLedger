package com.example.myledger.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.*
import com.example.myledger.ui.components.EntryDateDialog
import com.example.myledger.ui.records.*
import com.example.myledger.ui.theme.MyLedgerTheme
import com.example.myledger.ui.transaction.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w320dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinalLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 3)
    private val note = "详细备注包含换行与中文\n".repeat(80)

    @Test fun narrowLargeFontRecordKeepsCategoryAndExactAmountAndLimitsNotePreview() {
        val row = TransactionEntity(id = 1, type = TransactionType.EXPENSE, categoryId = 8,
            amountMinor = Long.MAX_VALUE, date = date, note = note)
        var opened = 0L
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                MyLedgerTheme(darkTheme = true) { Surface {
                    RecordsScreen(RecordsUiState(days = RecordsGrouping.group(listOf(row), DefaultCategories.all, emptyList()), isLoading = false),
                        onOpen = { opened = it }, onRange = {}, onRetry = {})
                } }
            }
        }
        compose.onNodeWithTag("records_list").performScrollToNode(hasTestTag("record_1"))
        compose.onNodeWithText("医疗健康", useUnmergedTree = true).assertIsDisplayed()
        val amount = compose.onNodeWithText("−¥92233720368547758.07", useUnmergedTree = true)
        amount.assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        amount.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        screenshot("records-narrow-dark-large-font")
        // Paragraph widths retain subpixel fractions; node bounds round to whole pixels.
        assertTrue(results.single().getLineRight(0) <= amount.fetchSemanticsNode().boundsInRoot.width + 1f)
        assertFalse(results.single().didOverflowHeight)
        assertEquals(1, results.single().lineCount)
        val noteLayout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(note, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(noteLayout) }
        assertEquals(2, noteLayout.single().lineCount)
        compose.onNodeWithTag("record_1").performClick(); assertEquals(1L, opened)
        screenshot("records-narrow-dark-large-font")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land")
    fun landscapeLongNoteEditorCanChooseCategoryAndSaveWithoutRetainingFieldFocus() {
        var saved: TransactionForm? = null
        compose.setContent {
            var form by remember { mutableStateOf(TransactionForm(date = date, amount = "1234567890.12", note = note)) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                MyLedgerTheme(darkTheme = false) { Surface {
                    TransactionScreen(TransactionUiState(form, allCategories = DefaultCategories.all, isLoading = false),
                        onType = { form = form.copy(type = it) }, onAmount = { form = form.copy(amount = it) },
                        onCategory = { form = form.copy(categoryId = it) }, onActivity = {}, onReimbursable = {},
                        onDate = {}, onNote = { form = form.copy(note = it) }, onSave = { saved = form }, onRetry = {})
                } }
            }
        }
        compose.onNodeWithTag("transaction_amount").performClick().assertIsFocused()
        compose.onNodeWithTag("transaction_category").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_amount").assertIsNotFocused()
        compose.onNodeWithText("交通").performScrollTo().performClick()
        compose.onNodeWithTag("transaction_note").performScrollTo().performClick().assertIsFocused()
        compose.onNodeWithTag("transaction_save").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("transaction_note").assertIsNotFocused()
        assertEquals(note, saved!!.note); assertEquals("1234567890.12", saved!!.amount); assertEquals(2L, saved!!.categoryId)
        screenshot("editor-landscape-long-note")
    }

    @Test
    @Config(qualifiers = "zh-rCN-w640dp-h360dp-land")
    fun landscapeDateDialogUsesInputAndConfirmsBusinessDate() {
        var selected: LocalDate? = null
        compose.setContent {
            MyLedgerTheme(darkTheme = false) { EntryDateDialog(date, onSelected = { selected = it }, onDismiss = {}) }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("20261005")
        compose.onNodeWithText("确定").assertIsDisplayed()
        screenshot("date-landscape-input")
        compose.onNodeWithText("确定").performClick()
        assertEquals(date.plusDays(2), selected)
    }

    private fun screenshot(name: String) {
        val file = File("build/stage12-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val dialog = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }
            val view = dialog?.window?.decorView ?: ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single().window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
