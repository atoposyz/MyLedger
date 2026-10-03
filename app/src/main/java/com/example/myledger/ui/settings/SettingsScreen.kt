package com.example.myledger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myledger.R
import com.example.myledger.util.AppVersion
import com.example.myledger.LedgerApplication
import com.example.myledger.data.settings.ThemeMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

@Composable
fun SettingsRoute(onOpenActivities: () -> Unit, onOpenBackup: () -> Unit) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application) { viewModelFactory { initializer { SettingsViewModel(application.settingsRepository) } } }
    val vm: SettingsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    SettingsScreen(state, vm::setTheme, vm::reload, onOpenActivities, onOpenBackup, versionName = AppVersion.name(application))
}

@Composable
fun SettingsScreen(state: SettingsUiState, onTheme: (ThemeMode) -> Unit, onRetry: () -> Unit,
    onOpenActivities: () -> Unit, onOpenBackup: () -> Unit, modifier: Modifier = Modifier, versionName: String = "") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 96.dp)
            .testTag("settings_list"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedButton(onClick = onOpenActivities, modifier = Modifier.fillMaxWidth().testTag("settings_activities")) {
                Text(stringResource(R.string.activities))
            }
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("settings_theme_${mode.name}")
                        .selectable(selected = state.settings.themeMode == mode, enabled = !state.isLoading && !state.isSaving && !state.readFailed,
                            role = Role.RadioButton, onClick = { onTheme(mode) }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.settings.themeMode == mode, onClick = null)
                        Text(stringResource(when (mode) { ThemeMode.SYSTEM -> R.string.settings_system; ThemeMode.LIGHT -> R.string.settings_light; ThemeMode.DARK -> R.string.settings_dark }),
                            modifier = Modifier.padding(start = 12.dp).align(androidx.compose.ui.Alignment.CenterVertically))
                    }
                }
            }
            if (state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.readFailed) {
                Text(stringResource(R.string.settings_read_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, modifier = Modifier.testTag("settings_retry")) { Text(stringResource(R.string.transaction_retry)) }
            }
            if (state.saveFailed) Text(stringResource(R.string.settings_save_error), color = MaterialTheme.colorScheme.error)
            HorizontalDivider(); Text(stringResource(R.string.settings_currency), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.settings_cny))
            HorizontalDivider(); Text(stringResource(R.string.settings_backup), style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenBackup, modifier = Modifier.fillMaxWidth().testTag("settings_backup")) { Text(stringResource(R.string.settings_backup)) }
            HorizontalDivider(); Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium)
            Text("MyLedger $versionName", modifier = Modifier.testTag("settings_version"))
            Text(stringResource(R.string.settings_local_description), style = MaterialTheme.typography.bodySmall)
        }
    }
}
