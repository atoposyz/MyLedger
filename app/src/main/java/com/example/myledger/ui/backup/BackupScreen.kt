package com.example.myledger.ui.backup

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.backup.BackupMetadata
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupRoute(onBusyChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.backup_share)
    val application = context.applicationContext as LedgerApplication
    val factory = remember(application) { viewModelFactory { initializer { BackupViewModel(application.backupManager, createSavedStateHandle()) } } }
    val vm: BackupViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    SideEffect { onBusyChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    BackHandler(enabled = state.busy) {}
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::saveExport) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    BackupScreen(state, vm::prepareExport, onSave = {
        save.launch("MyLedger-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(requireNotNull(state.export).metadata.createdAt)}.zip")
    }, onShare = {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backupfiles", vm.exportFile())
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("MyLedger backup", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, shareTitle))
        } catch (_: Exception) { vm.shareFailed() }
    }, onImport = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        vm::importSafetyCopy, vm::cancelPreview, vm::restoreConfirmed)
}

@Composable
fun BackupScreen(state: BackupUiState, onExport: () -> Unit, onSave: () -> Unit, onShare: () -> Unit,
    onImport: () -> Unit, onSafetyCopy: () -> Unit, onCancel: () -> Unit, onRestore: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.preview?.fileName) { if (state.preview == null) confirm = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 32.dp).testTag("backup_list"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.backup_plaintext), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onExport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_generate")) { Text(stringResource(R.string.backup_generate)) }
        state.export?.let { backup ->
            Text(stringResource(R.string.backup_ready), style = MaterialTheme.typography.titleMedium)
            BackupInfo(backup.metadata)
            OutlinedButton(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_save")) { Text(stringResource(R.string.backup_save)) }
            TextButton(onClick = onShare, enabled = !state.busy, modifier = Modifier.testTag("backup_share")) { Text(stringResource(R.string.backup_share)) }
        }
        HorizontalDivider()
        OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_open")) { Text(stringResource(R.string.settings_import)) }
        if (state.hasSafetyCopy) TextButton(onClick = onSafetyCopy, enabled = !state.busy, modifier = Modifier.testTag("backup_safety")) { Text(stringResource(R.string.backup_safety)) }
        state.preview?.let { backup ->
            Text(stringResource(R.string.backup_preview), style = MaterialTheme.typography.titleMedium)
            BackupInfo(backup.metadata)
            Text(stringResource(R.string.backup_replace_warning), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { confirm = true }, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_restore")) { Text(stringResource(R.string.backup_restore)) }
            TextButton(onClick = onCancel, enabled = !state.busy, modifier = Modifier.testTag("backup_cancel")) { Text(stringResource(R.string.cancel)) }
        }
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("backup_busy"))
        state.message?.let { message ->
            val successful = message == BackupMessage.SAVED || message == BackupMessage.RESTORED
            Text(if (message == BackupMessage.RESTORED) stringResource(R.string.backup_restored, state.restoredCount) else stringResource(when (message) {
                BackupMessage.SAVED -> R.string.backup_saved
                BackupMessage.INVALID_FILE -> R.string.backup_invalid
                BackupMessage.EXPORT_FAILED -> R.string.backup_export_failed
                BackupMessage.IMPORT_FAILED -> R.string.backup_import_failed
                BackupMessage.RESTORE_FAILED -> R.string.backup_restore_failed
                BackupMessage.SAVE_FAILED -> R.string.backup_save_failed
                BackupMessage.SHARE_FAILED -> R.string.backup_share_failed
                BackupMessage.FILE_EXPIRED -> R.string.backup_expired
                BackupMessage.RESTORED -> R.string.backup_saved
            }), color = if (successful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup_message"))
        }
    }
    if (confirm && state.preview != null) AlertDialog(onDismissRequest = { if (!state.busy) confirm = false },
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = { Text(stringResource(R.string.backup_confirm_message, state.preview.metadata.transactionCount)) },
        confirmButton = { TextButton(onClick = { confirm = false; onRestore() }, enabled = !state.busy, modifier = Modifier.testTag("backup_confirm")) { Text(stringResource(R.string.backup_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = !state.busy, modifier = Modifier.testTag("backup_confirm_cancel")) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun BackupInfo(metadata: BackupMetadata) {
    Text(stringResource(R.string.backup_info,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(metadata.createdAt),
        metadata.appVersion, metadata.transactionCount, metadata.activityCount), style = MaterialTheme.typography.bodyMedium)
}
