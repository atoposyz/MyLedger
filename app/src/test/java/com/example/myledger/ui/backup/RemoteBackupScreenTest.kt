package com.example.myledger.ui.backup

import android.app.Application
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.myledger.backup.*
import com.example.myledger.ui.theme.MyLedgerTheme
import java.time.Instant
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class, qualifiers = "zh-rCN-w411dp-h600dp-night")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteBackupScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unconfiguredStateDisablesNetworkingAndProvidesConfiguration() {
        var configure = false
        compose.setContent { MyLedgerTheme(darkTheme = true) { Surface {
            RemoteBackupScreen(RemoteBackupUiState(ready = true), { configure = true }, {}, {}, {}, {}, {}, {}, {})
        } } }
        compose.onNodeWithTag("remote_unconfigured").assertExists()
        compose.onNodeWithTag("remote_upload").assertIsNotEnabled()
        compose.onNodeWithTag("remote_refresh").assertIsNotEnabled()
        compose.onNodeWithTag("remote_configure").performClick(); assertTrue(configure)
    }
    @Test fun downloadedPreviewAndServerDeletionRequireSeparateConfirmation() {
        val entry = RemoteBackup("00000000-0000-0000-0000-000000000001", Instant.parse("2026-10-04T00:00:00Z"), 1024, "a".repeat(64))
        var restored = 0; var deleted = 0
        compose.setContent { MyLedgerTheme(darkTheme = true) { Surface {
            var state by remember { mutableStateOf(RemoteBackupUiState(configured = true, ready = true, loaded = true, entries = listOf(entry))) }
            RemoteBackupScreen(state, {}, {}, {}, { state = state.copy(preview = PreparedBackup("import-test.zip", BackupMetadata(1, "0.3.0", entry.createdAt, 1, 0))) },
                {}, {}, { restored++ }, { deleted++ })
        } } }
        compose.onNodeWithTag("remote_delete_${entry.id}").performScrollTo().performClick(); assertEquals(0, deleted)
        compose.onNodeWithText("取消").performClick(); assertEquals(0, deleted)
        compose.onNodeWithTag("remote_delete_${entry.id}").performScrollTo().performClick()
        compose.onNodeWithTag("remote_delete_confirm").performClick(); assertEquals(1, deleted)
        compose.onNodeWithTag("remote_download_${entry.id}").performScrollTo().performClick(); assertEquals(0, restored)
        compose.onNodeWithTag("remote_restore").performScrollTo().performClick(); assertEquals(0, restored)
        compose.onNodeWithTag("remote_restore_confirm").performClick(); assertEquals(1, restored)
    }
}
