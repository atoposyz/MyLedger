package com.example.myledger

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationSelectsEachPage() {
        selectTab("首页", "账本首页")
        selectRecordsTab()
        selectTab("统计", "暂无统计")
        selectTab("设置", "暂无设置项")
        selectTab("首页", "账本首页")
    }

    @Test
    fun bothEntryModesReturnToOriginatingPage() {
        selectRecordsTab()
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("记一笔").performClick()
        compose.onNodeWithTag("transaction_amount").assertIsDisplayed()
        compose.onNodeWithContentDescription("记账").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("明细") and hasClickAction()).assertIsSelected()

        selectTab("统计", "暂无统计")
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("记多笔").performClick()
        compose.onNodeWithTag("batch_default_date").assertIsDisplayed()
        pressSystemBack()
        compose.onNodeWithText("暂无统计").assertIsDisplayed()
        compose.onNode(hasText("统计") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun dismissingEntryChoiceStaysOnCurrentPage() {
        selectTab("设置", "暂无设置项")
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("选择记账方式").assertDoesNotExist()
        compose.onNodeWithText("暂无设置项").assertIsDisplayed()

        compose.onNodeWithContentDescription("记账").performClick()
        pressSystemBack()
        compose.onNodeWithText("选择记账方式").assertDoesNotExist()
        compose.onNode(hasText("设置") and hasClickAction()).assertIsSelected()
    }

    @Test
    fun recreationRestoresEntryAndBackDestination() {
        selectTab("设置", "暂无设置项")
        compose.onNodeWithContentDescription("记账").performClick()
        compose.onNodeWithText("记多笔").performClick()
        compose.onNodeWithTag("batch_default_date").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("batch_default_date").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("设置") and hasClickAction()).assertIsSelected()
    }

    private fun selectTab(label: String, emptyTitle: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithText(emptyTitle).assertIsDisplayed()
    }

    private fun selectRecordsTab() {
        compose.onNode(hasText("明细") and hasClickAction()).performClick().assertIsSelected()
        compose.onNodeWithTag("records_filter").assertIsDisplayed()
    }

    private fun pressSystemBack() {
        // Dispatch to the focused window so this also exercises dialog dismissal.
        Espresso.pressBack()
    }
}
