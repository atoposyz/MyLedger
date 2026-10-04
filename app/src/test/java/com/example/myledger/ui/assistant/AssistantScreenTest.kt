package com.example.myledger.ui.assistant

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myledger.assistant.*
import com.example.myledger.ui.theme.MyLedgerTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w891dp-h411dp-land")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun configuredSendAndReadOnlyEvidenceRemainReachableInLandscape() {
        var question = ""; var clear = 0
        val evidence = ToolEvidence("get_reimbursement_summary", "{}", "{\"reimbursable_minor\":\"100\",\"reimbursement_received_minor\":\"200\",\"unmatched_difference_minor\":\"-100\",\"rule\":\"未做逐笔匹配\"}")
        compose.setContent { MyLedgerTheme(darkTheme = true) { Surface {
            AssistantScreen(AssistantUiState(configured = true, allowed = true, ready = true, turns = listOf(AssistantTurn("报销情况？", "到账多于本期垫付。", listOf(evidence)))),
                {}, { question = it }, {}, {}, { clear++ }, { _, _, _, _, _, _ -> })
        } } }
        compose.onNodeWithTag("assistant_question").performScrollTo().performTextInput("本月支出？")
        compose.onNodeWithTag("assistant_send").performScrollTo().performClick(); assertEquals("本月支出？", question)
        compose.onNodeWithText("到账多于本期垫付。").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("垫付与到账差额（未匹配）：−¥1.00", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("assistant_clear").performScrollTo().performClick(); assertEquals(0, clear)
        compose.onNodeWithText("确认清空").performClick(); assertEquals(1, clear)
    }
}
