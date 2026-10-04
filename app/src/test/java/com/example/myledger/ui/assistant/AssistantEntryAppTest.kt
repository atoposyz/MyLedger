package com.example.myledger.ui.assistant

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantEntryAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun unconfiguredEntryNavigationAndDescriptionSurviveRotationWithoutLedgerWrites() {
        val app = RuntimeEnvironment.getApplication() as LedgerApplication
        val original = runBlocking { app.repository.snapshot() }
        val originalTheme = runBlocking { app.settingsRepository.current().themeMode }
        runBlocking { app.settingsRepository.setTheme(ThemeMode.DARK) }
        compose.onNode(hasText("助手") and hasClickAction()).performClick()
        compose.onNodeWithTag("assistant_entry").performClick()
        compose.onNodeWithTag("ai_entry_unconfigured").assertExists()
        compose.onNodeWithTag("ai_entry_description").performScrollTo().performTextInput("昨晚吃饭32元")
        compose.onNodeWithTag("ai_entry_generate").performScrollTo().assertIsNotEnabled()
        screenshot("ai-entry-dark")
        compose.activityRule.scenario.recreate(); compose.waitForIdle()
        compose.onNodeWithTag("ai_entry_description").performScrollTo().assertTextContains("昨晚吃饭32元")
        compose.onNodeWithTag("ai_entry_configure").performScrollTo().performClick()
        compose.onNodeWithTag("config_allow_entry").performScrollTo().assertIsOff()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("ai_entry_description").performScrollTo().assertTextContains("昨晚吃饭32元")
        assertEquals(original, runBlocking { app.repository.snapshot() })
        runBlocking { app.settingsRepository.setTheme(originalTheme) }
    }
    private fun screenshot(name: String) {
        val file = File("build/v05-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle { val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
