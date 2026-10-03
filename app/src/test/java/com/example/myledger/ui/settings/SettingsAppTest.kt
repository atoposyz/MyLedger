package com.example.myledger.ui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.settings.ThemeMode
import java.io.File
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
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h600dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository get() = (compose.activity.application as LedgerApplication).settingsRepository
    @After fun resetTheme() = runBlocking { repository.setTheme(ThemeMode.SYSTEM) }
    private fun choose(mode: ThemeMode) {
        val tag = "settings_theme_${mode.name}"
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        compose.onNodeWithTag(tag).performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { repository.current().themeMode == mode } }
        compose.onNodeWithTag(tag).assertIsSelected()
    }
    @Test fun themeOverridesSystemAndPersistsAcrossRecreationAndNavigation() {
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        choose(ThemeMode.LIGHT); screenshot("settings-light")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("settings_theme_LIGHT").assertIsSelected()
        choose(ThemeMode.DARK); screenshot("settings-dark")
        compose.onNode(hasText("首页") and hasClickAction()).performClick()
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        compose.onNodeWithTag("settings_theme_DARK").assertIsSelected()
        choose(ThemeMode.SYSTEM)
        compose.onNodeWithText("人民币（CNY），金额精确到分").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("settings_version").performScrollTo().assertTextContains("MyLedger", substring = true)
    }
    private fun screenshot(name: String) {
        val file = File("build/stage10-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
