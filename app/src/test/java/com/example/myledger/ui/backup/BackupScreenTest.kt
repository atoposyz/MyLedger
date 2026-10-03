package com.example.myledger.ui.backup

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myledger.backup.*
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
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h600dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackupScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyBackupStillRequiresSecondConfirmationAndBusyStateBlocksActions() {
        val preview = PreparedBackup("import-test.zip", BackupMetadata(1, "0.1.0-test", Instant.parse("2026-10-03T12:00:00Z"), 0, 0))
        var state by mutableStateOf(BackupUiState(preview = preview))
        var restores = 0
        compose.setContent { MyLedgerTheme(dynamicColor = false) { Surface {
            BackupScreen(state, {}, {}, {}, {}, {}, {}, { restores++ })
        } } }
        compose.onNodeWithTag("backup_restore").performScrollTo().performClick(); assertEquals(0, restores)
        compose.onNodeWithTag("backup_confirm_cancel").performClick(); assertEquals(0, restores)
        compose.onNodeWithTag("backup_restore").performClick(); compose.onNodeWithTag("backup_confirm").performClick(); assertEquals(1, restores)
        compose.runOnIdle { state = state.copy(busy = true) }
        compose.onNodeWithTag("backup_restore").assertIsNotEnabled()
        compose.onNodeWithTag("backup_open").assertIsNotEnabled()
    }
}
