package com.example.myledger.ui.backup

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.backup.BackupCodec
import com.example.myledger.backup.WindowsFileProviderPaths
import com.example.myledger.data.local.entity.*
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val marker = "Stage11-${UUID.randomUUID()}"
    private val app get() = compose.activity.application as LedgerApplication
    @Before fun adaptWindowsPaths() { WindowsFileProviderPaths.install(compose.activity) }
    @After fun cleanOwnRows() = runBlocking {
        app.repository.observeTransactions().first().filter { it.note?.startsWith(marker) == true }.forEach { app.repository.deleteTransaction(it.id) }
    }
    private fun add(amount: Long) = runBlocking { app.repository.addTransaction(TransactionEntity(type = TransactionType.EXPENSE,
        amountMinor = amount, categoryId = 1, date = LocalDate.now(), note = marker)) }
    private fun enabled(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun openBackup() {
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        compose.onNodeWithTag("settings_backup").performScrollTo().performClick()
        compose.onNodeWithContentDescription("记账").assertDoesNotExist(); enabled("backup_generate")
    }
    private fun uri(file: File): Uri = FileProvider.getUriForFile(compose.activity, "${compose.activity.packageName}.backupfiles", file)
    private fun completePicker(tag: String, action: String, target: Uri) {
        enabled(tag).performClick()
        val launched = shadowOf(compose.activity).nextStartedActivityForResult
        assertEquals(action, launched.intent.action)
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(launched.intent, Activity.RESULT_OK, Intent().setData(target)) }
    }
    private fun waitMessage(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("backup_message") and hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun saveAndShareUseContentUrisAndRestoreRequiresConfirmationSurvivesRotationAndCanUndo() {
        val first = add(1234); val original = runBlocking { app.repository.snapshot() }
        openBackup(); enabled("backup_generate").performClick(); enabled("backup_save")
        val chosen = File(compose.activity.cacheDir, "backups/exports/chosen-${UUID.randomUUID()}.zip")
        completePicker("backup_save", Intent.ACTION_CREATE_DOCUMENT, uri(chosen))
        waitMessage("备份文件已保存。")
        assertEquals(original, chosen.inputStream().use { BackupCodec.read(it) }.ledger)
        enabled("backup_share").performClick()
        var chooser = shadowOf(compose.activity).nextStartedActivity
        while (chooser != null && chooser.action != Intent.ACTION_CHOOSER) chooser = shadowOf(compose.activity).nextStartedActivity
        assertNotNull(chooser)
        val shared = chooser!!.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        assertEquals(Intent.ACTION_SEND, shared.action); assertEquals("application/zip", shared.type)
        assertEquals("content", shared.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!.scheme)
        assertTrue(shared.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        val other = add(999); val before = runBlocking { app.repository.snapshot() }
        completePicker("backup_open", Intent.ACTION_OPEN_DOCUMENT, uri(chosen)); enabled("backup_restore")
        assertEquals(before, runBlocking { app.repository.snapshot() })
        screenshot("backup-preview-light")
        enabled("backup_restore").performClick()
        compose.onNodeWithTag("backup_confirm_cancel").performClick()
        assertEquals(before, runBlocking { app.repository.snapshot() })
        enabled("backup_restore").performClick(); compose.activityRule.scenario.recreate()
        compose.onNodeWithText("确认替换当前账本？").assertIsDisplayed()
        compose.onNodeWithTag("backup_confirm").performClick()
        waitMessage("已恢复 1 笔账目。首页、明细与统计会同步更新。")
        assertEquals(original, runBlocking { app.repository.snapshot() }); assertNotNull(runBlocking { app.repository.getTransaction(first) })
        assertNull(runBlocking { app.repository.getTransaction(other) })
        enabled("backup_safety").performClick(); enabled("backup_restore").performClick()
        compose.onNodeWithTag("backup_confirm").performClick()
        waitMessage("已恢复 2 笔账目。首页、明细与统计会同步更新。")
        assertEquals(before, runBlocking { app.repository.snapshot() })
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("首页") and hasClickAction()).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("home_balance") and hasText("−¥22.33")).fetchSemanticsNodes().isNotEmpty() }
    }
    @Test
    @Config(qualifiers = "zh-rCN-w411dp-h600dp-night")
    fun invalidSelectedFileShowsErrorAndNeverEnablesRestoreOrChangesLedger() {
        add(1234); val original = runBlocking { app.repository.snapshot() }; openBackup()
        val bad = File(compose.activity.cacheDir, "backups/exports/bad-${UUID.randomUUID()}.zip")
        bad.parentFile?.mkdirs(); bad.writeText("not a backup")
        completePicker("backup_open", Intent.ACTION_OPEN_DOCUMENT, uri(bad))
        waitMessage("备份损坏、不完整或版本不受支持；当前账本未更改。")
        compose.onNodeWithTag("backup_restore").assertDoesNotExist()
        assertEquals(original, runBlocking { app.repository.snapshot() }); screenshot("backup-invalid-dark")
    }
    private fun screenshot(name: String) {
        val file = File("build/stage11-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView; val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
