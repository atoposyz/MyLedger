package com.example.myledger.ui.assistant

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.data.local.entity.*
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h891dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = RuntimeEnvironment.getApplication() as LedgerApplication
    private var recordId = 0L
    @After fun close() = runBlocking { if (recordId > 0) app.repository.deleteTransaction(recordId) }
    private fun ready(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    @Test fun fifthTabQueriesActualLedgerOfflineAndConfigurationSurvivesRotationWithoutSecrets() {
        recordId = runBlocking { app.repository.addTransaction(TransactionEntity(type = TransactionType.EXPENSE, amountMinor = 1234, categoryId = 1,
            date = LocalDate.of(2022, 1, 17), note = "assistant native test private note")) }
        val original = runBlocking { app.repository.snapshot() }
        compose.onNode(hasText("助手") and hasClickAction()).performClick()
        ready("assistant_start").performTextReplacement("2022-01-17")
        ready("assistant_end").performTextReplacement("2022-01-17")
        ready("assistant_query").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("assistant_local_result").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("assistant_local_result").performScrollTo().assertTextContains("日常支出：¥12.34", substring = true)
        compose.onNodeWithTag("assistant_send").performScrollTo().assertIsNotEnabled()
        screenshot("assistant-local-dark")
        ready("assistant_configure").performClick()
        ready("config_server").performTextInput("https://backup.example.com")
        ready("config_token").performTextInput("test-unsaved-backup-secret")
        ready("config_api").performTextInput("https://api.example.com/v1")
        ready("config_model").performTextInput("test-model")
        ready("config_key").performTextInput("test-unsaved-api-key")
        compose.activityRule.scenario.recreate()
        ready("config_key").assertTextContains("")
        val keyValue = compose.onNodeWithTag("config_key").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        val tokenValue = ready("config_token").fetchSemanticsNode().config[SemanticsProperties.EditableText].text
        assertEquals("", keyValue); assertEquals("", tokenValue)
        ready("config_api").assertTextContains("https://api.example.com/v1")
        ready("config_model").assertTextContains("test-model")
        assertFalse(runBlocking { app.integrationSettings.current().allowAggregates })
        assertEquals(original, runBlocking { app.repository.snapshot() })
        screenshot("configuration-after-rotation")
    }
    @Test fun missingServerCanOpenConfigurationAndReturnWithoutNetworking() {
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        ready("settings_remote").performClick()
        compose.onNodeWithTag("remote_unconfigured").assertExists()
        compose.onNodeWithTag("remote_upload").assertIsNotEnabled()
        screenshot("remote-unconfigured-dark")
        ready("remote_configure").performClick(); ready("config_server")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("remote_unconfigured").assertExists()
    }
    private fun screenshot(name: String) {
        val file = File("build/v04-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle { val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
    }
}
