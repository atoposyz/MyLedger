package com.example.myledger.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w891dp-h411dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FinalAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun enabled(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun tab(name: String) { compose.onNode(hasText(name) and hasClickAction()).performClick() }

    @Test fun landscapeNavigationAndBackupStayReachableAndPortraitRecreationKeepsUnsavedForm() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("home_balance").fetchSemanticsNodes().isNotEmpty() }
        screenshot("home-landscape")
        tab("明细"); compose.onNodeWithTag("records_filter").assertIsDisplayed()
        tab("统计"); compose.onNodeWithTag("statistics_scope_ALL").performClick().assertIsSelected()
        tab("设置"); enabled("settings_backup").performClick()
        enabled("backup_generate").performClick(); enabled("backup_save").assertIsDisplayed()
        screenshot("backup-landscape")
        compose.onNodeWithContentDescription("返回").performClick(); tab("首页")
        compose.onNodeWithContentDescription("记账").performClick(); compose.onNodeWithText("记一笔").performClick()
        enabled("transaction_category")
        compose.onNodeWithTag("transaction_amount").performScrollTo().performTextInput("1234567890.12")
        val note = "横竖屏切换不丢失草稿\n".repeat(30)
        compose.onNodeWithTag("transaction_note").performScrollTo().performTextInput(note)
        compose.onNodeWithTag("transaction_date").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and !hasTestTag("transaction_amount") and !hasTestTag("transaction_note"))
            .assertExists()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { RuntimeEnvironment.setQualifiers("zh-rCN-w411dp-h891dp-port") }
        compose.activityRule.scenario.recreate(); enabled("transaction_category")
        compose.onNodeWithTag("transaction_amount").assertTextContains("1234567890.12")
        compose.onNodeWithTag("transaction_note").performScrollTo().assertTextContains(note)
        compose.onNodeWithTag("transaction_save").performScrollTo().assertIsDisplayed()
        screenshot("editor-portrait-after-rotation")
    }

    private fun screenshot(name: String) {
        val file = File("build/stage12-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
