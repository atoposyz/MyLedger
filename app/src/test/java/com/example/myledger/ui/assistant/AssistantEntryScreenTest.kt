package com.example.myledger.ui.assistant

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myledger.ui.theme.MyLedgerTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantEntryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unconfiguredDraftGenerationIsDisabledAndConfigurationRemainsReachable() {
        var config = 0
        compose.setContent { MyLedgerTheme { Surface { AssistantEntryScreen(AssistantEntryUiState(ready = true), { config++ }, {}, {}, {}) } } }
        compose.onNodeWithTag("ai_entry_generate").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("ai_entry_configure").performScrollTo().performClick(); assertEquals(1, config)
    }
    @Test fun onlyExplicitGenerateSendsEnteredDescriptionAndBusyRequestCanBeCancelled() {
        var description = ""; var cancel = 0
        val state = mutableStateOf(AssistantEntryUiState(ready = true, configured = true, allowed = true))
        compose.setContent { MyLedgerTheme(darkTheme = true) { Surface {
            AssistantEntryScreen(state.value, {}, { description = it; state.value = state.value.copy(busy = true) }, { cancel++ }, {})
        } } }
        compose.onNodeWithTag("ai_entry_description").performScrollTo().performTextInput("今晚吃饭32块")
        assertEquals("", description)
        compose.onNodeWithTag("ai_entry_generate").performScrollTo().performClick(); assertEquals("今晚吃饭32块", description)
        compose.onNodeWithTag("ai_entry_generate").assertIsNotEnabled()
        compose.onNodeWithTag("ai_entry_cancel").performScrollTo().performClick(); assertEquals(1, cancel)
    }
}
