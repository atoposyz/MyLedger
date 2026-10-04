package com.example.myledger.ui.backup

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myledger.backup.*
import com.example.myledger.data.settings.BackupPreferences
import com.example.myledger.ui.theme.MyLedgerTheme
import java.time.Instant
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h600dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupHistoryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun passwordDialogCanTypeValidateAndSubmitWithoutPersistingPassword() {
        var submitted = ""
        compose.setContent {
            MyLedgerTheme(darkTheme = false) { Surface {
                BackupScreen(BackupUiState(preferencesReady = true), {}, {}, {}, {}, {}, {}, {},
                    onEncryptedExport = { password, _ -> submitted = String(password); password.fill('\u0000') })
            } }
        }
        compose.onNodeWithTag("backup_generate_encrypted").performScrollTo().performClick()
        compose.onNodeWithTag("backup_password").performScrollTo().performTextInput("Backup-test-2026")
        compose.onNodeWithTag("backup_password_repeat").performScrollTo().performTextInput("Backup-test-2026")
        compose.onNodeWithTag("backup_password_confirm").performScrollTo().performClick()
        assertEquals("Backup-test-2026", submitted)
    }
    @Test fun historyActionsRequireConfirmationAndAutomaticToggleReflectsPersistedState() {
        val name = "history-00000000-0000-0000-0000-000000000001.enc"
        val metadata = BackupMetadata(1, "0.2.0", Instant.parse("2026-10-04T00:00:00Z"), 2, 1)
        var deleted: String? = null; var restored = 0; var toggled: Boolean? = null
        compose.setContent {
            var state by remember { mutableStateOf(BackupUiState(preferencesReady = true, history = listOf(HistoryBackup(name, metadata, 1024)))) }
            MyLedgerTheme(darkTheme = true) { Surface {
                BackupScreen(state, {}, {}, {}, {}, {}, {}, { restored++ },
                    onAutomatic = { toggled = it; state = state.copy(preferences = BackupPreferences(automatic = it)) },
                    onHistory = { state = state.copy(preview = PreparedBackup("import-00000000-0000-0000-0000-000000000001.zip", metadata)) },
                    onDeleteHistory = { deleted = it })
            } }
        }
        compose.onNodeWithTag("backup_automatic").performScrollTo().assertIsOff().performClick().assertIsOn()
        assertEquals(true, toggled)
        compose.onNodeWithTag("backup_history_delete_$name").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick(); assertNull(deleted)
        compose.onNodeWithTag("backup_history_delete_$name").performScrollTo().performClick()
        compose.onNodeWithTag("backup_delete_history_confirm").performClick(); assertEquals(name, deleted)
        compose.onNodeWithTag("backup_history_restore_$name").performScrollTo().performClick(); assertEquals(0, restored)
        compose.onNodeWithTag("backup_restore").performScrollTo().performClick(); assertEquals(0, restored)
        compose.onNodeWithTag("backup_confirm").performClick(); assertEquals(1, restored)
    }
}
