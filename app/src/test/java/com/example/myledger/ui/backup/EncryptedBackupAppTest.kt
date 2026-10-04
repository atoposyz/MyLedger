package com.example.myledger.ui.backup

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import com.example.myledger.LedgerApplication
import com.example.myledger.MainActivity
import com.example.myledger.backup.BackupCodec
import com.example.myledger.backup.BackupEncryption
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
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerApplication::class, qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EncryptedBackupAppTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val app get() = compose.activity.application as LedgerApplication
    private val marker = "Encrypted-backup-${UUID.randomUUID()}"
    private val password = "Backup-test-2026"
    @Before fun paths() { WindowsFileProviderPaths.install(compose.activity) }
    @After fun cleanup() = runBlocking {
        app.repository.observeTransactions().first().filter { it.note == marker }.forEach { app.repository.deleteTransaction(it.id) }
    }
    private fun add(amount: Long) = runBlocking { app.repository.addTransaction(TransactionEntity(type = TransactionType.EXPENSE,
        amountMinor = amount, categoryId = 1, date = LocalDate.now(), note = marker)) }
    private fun enabled(tag: String): SemanticsNodeInteraction {
        compose.waitUntil(20_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().any { !it.config.contains(SemanticsProperties.Disabled) } }
        return compose.onNodeWithTag(tag).performScrollTo()
    }
    private fun picker(tag: String, file: File) {
        enabled(tag).performClick()
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        val uri = FileProvider.getUriForFile(compose.activity, "${compose.activity.packageName}.backupfiles", file)
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(started.intent, Activity.RESULT_OK, Intent().setData(uri)) }
    }
    @Test fun encryptedFileExportWrongPasswordRotationAndConfirmedRestoreWorkThroughSystemPickers() {
        add(1234); val original = runBlocking { app.repository.snapshot() }
        compose.onNode(hasText("设置") and hasClickAction()).performClick()
        compose.onNodeWithTag("settings_backup").performScrollTo().performClick()
        enabled("backup_generate_encrypted").performClick()
        compose.onNodeWithTag("backup_password").performScrollTo().performTextInput("short")
        compose.onNodeWithTag("backup_password_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("backup_password").performScrollTo().performTextReplacement(password)
        compose.onNodeWithTag("backup_password_repeat").performScrollTo().performTextInput("different")
        compose.onNodeWithText("两次输入的密码不一致").assertExists()
        compose.onNodeWithTag("backup_password_confirm").assertIsNotEnabled()
        compose.onNodeWithTag("backup_password_repeat").performScrollTo().performTextReplacement(password)
        compose.onNodeWithTag("backup_password_confirm").performScrollTo().performClick()
        val chosen = File(compose.activity.cacheDir, "backups/exports/chosen-${UUID.randomUUID()}.enc")
        picker("backup_save", chosen)
        compose.waitUntil(20_000) { chosen.isFile && chosen.length() > 56 && compose.onAllNodesWithText("备份文件已保存。").fetchSemanticsNodes().isNotEmpty() }
        val decrypted = BackupEncryption.decrypt(chosen.readBytes(), password.toCharArray())
        assertEquals(original, BackupCodec.read(decrypted.inputStream()).ledger)
        add(999); val before = runBlocking { app.repository.snapshot() }
        picker("backup_open", chosen)
        enabled("backup_password").performTextInput("wrong-password")
        compose.onNodeWithTag("backup_password_confirm").performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("密码错误或备份已损坏，请检查后重试。").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(before, runBlocking { app.repository.snapshot() })
        enabled("backup_password").performTextReplacement(password)
        compose.activityRule.scenario.recreate()
        assertEquals("", compose.onNodeWithTag("backup_password").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        screenshot("encrypted-unlock-after-rotation")
        compose.onNodeWithTag("backup_password").performScrollTo().performTextInput(password)
        compose.onNodeWithTag("backup_password_confirm").performScrollTo().performClick()
        enabled("backup_restore")
        assertEquals(before, runBlocking { app.repository.snapshot() })
        screenshot("encrypted-restore-preview")
        enabled("backup_restore").performClick(); compose.onNodeWithTag("backup_confirm").performClick()
        compose.waitUntil(20_000) { runBlocking { app.repository.snapshot() == original } }
        assertEquals(original, runBlocking { app.repository.snapshot() })
    }
    private fun screenshot(name: String) {
        val file = File("build/v02-ui/$name.png"); file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap)); file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
    }
}
