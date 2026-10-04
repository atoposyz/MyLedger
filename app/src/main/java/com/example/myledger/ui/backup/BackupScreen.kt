package com.example.myledger.ui.backup

import android.content.ClipData
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.semantics.Role
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
    val factory = remember(application) { viewModelFactory { initializer {
        BackupViewModel(application.backupManager, createSavedStateHandle(), application.settingsRepository, application.backupScheduler)
    } } }
    val vm: BackupViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    SideEffect { onBusyChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    BackHandler(enabled = state.busy) {}
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(vm::saveExport) }
    val saveEncrypted = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(vm::saveExport) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::import) }
    BackupScreen(state, vm::prepareExport, onSave = {
        val backup = requireNotNull(state.export)
        val name = "MyLedger-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault()).format(backup.metadata.createdAt)}"
        if (backup.encrypted) saveEncrypted.launch("$name.enc") else save.launch("$name.zip")
    }, onShare = {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.backupfiles", vm.exportFile())
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (state.export?.encrypted == true) "application/octet-stream" else "application/zip"; putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("MyLedger backup", uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, shareTitle))
        } catch (_: Exception) { vm.shareFailed() }
    }, onImport = { open.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
        vm::importSafetyCopy, vm::cancelPreview, vm::restoreConfirmed,
        onEncryptedExport = vm::encryptedExport, onUnlock = vm::unlock, onCancelUnlock = vm::cancelUnlock,
        onAutomatic = vm::setAutomatic, onLocalBackup = vm::localBackup, onHistory = vm::historyPreview, onDeleteHistory = vm::deleteHistory,
        onRetryPreferences = vm::reloadPreferences)
}

@Composable
fun BackupScreen(state: BackupUiState, onExport: () -> Unit, onSave: () -> Unit, onShare: () -> Unit,
    onImport: () -> Unit, onSafetyCopy: () -> Unit, onCancel: () -> Unit, onRestore: () -> Unit,
    onEncryptedExport: (CharArray, String?) -> Unit = { password, _ -> password.fill('\u0000') },
    onUnlock: (CharArray) -> Unit = { it.fill('\u0000') }, onCancelUnlock: () -> Unit = {},
    onAutomatic: (Boolean) -> Unit = {}, onLocalBackup: () -> Unit = {}, onHistory: (String) -> Unit = {}, onDeleteHistory: (String) -> Unit = {},
    onRetryPreferences: () -> Unit = {}) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf(false) }
    var selectedHistory by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingHistory by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(state.preview?.fileName) { if (state.preview == null) confirm = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp).padding(bottom = 32.dp).testTag("backup_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.backup_plaintext), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onExport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_generate")) { Text(stringResource(R.string.backup_generate)) }
            OutlinedButton(onClick = { selectedHistory = null; exportPassword = true }, enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("backup_generate_encrypted")) { Text(stringResource(R.string.backup_generate_encrypted)) }
            if (exportPassword) key(selectedHistory) {
                BackupPasswordForm(unlock = false, busy = state.busy, failed = false,
                    onDismiss = { exportPassword = false }, onConfirm = { password -> exportPassword = false; onEncryptedExport(password, selectedHistory) })
            }
            state.export?.let { backup ->
                Text(stringResource(R.string.backup_ready), style = MaterialTheme.typography.titleMedium)
                if (backup.encrypted) Text(stringResource(R.string.backup_encrypted_ready), style = MaterialTheme.typography.bodySmall)
                BackupInfo(backup.metadata)
                OutlinedButton(onClick = onSave, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_save")) { Text(stringResource(R.string.backup_save)) }
                TextButton(onClick = onShare, enabled = !state.busy, modifier = Modifier.testTag("backup_share")) { Text(stringResource(R.string.backup_share)) }
            }
            HorizontalDivider()
            OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_open")) { Text(stringResource(R.string.settings_import)) }
            if (state.lockedImport != null) key(state.lockedImport) {
                BackupPasswordForm(unlock = true, busy = state.busy,
                    failed = state.message == BackupMessage.UNLOCK_FAILED || state.message == BackupMessage.INVALID_FILE,
                    onDismiss = onCancelUnlock, onConfirm = onUnlock)
            }
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
                val successful = message in setOf(BackupMessage.SAVED, BackupMessage.RESTORED, BackupMessage.LOCAL_SAVED, BackupMessage.HISTORY_DELETED)
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
                    BackupMessage.UNLOCK_FAILED -> R.string.backup_unlock_failed
                    BackupMessage.HISTORY_FAILED -> R.string.backup_history_failed
                    BackupMessage.LOCAL_SAVED -> R.string.backup_local_saved
                    BackupMessage.AUTO_FAILED -> R.string.backup_auto_failed
                    BackupMessage.HISTORY_DELETED -> R.string.backup_history_deleted
                }), color = if (successful) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.testTag("backup_message"))
            }
            HorizontalDivider()
            Text(stringResource(R.string.backup_local_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.backup_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("backup_automatic")
                .toggleable(state.preferences.automatic, enabled = !state.busy && state.preferencesReady && !state.preferencesFailed,
                    role = Role.Switch, onValueChange = onAutomatic), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.backup_automatic), modifier = Modifier.weight(1f))
                Switch(checked = state.preferences.automatic, onCheckedChange = null,
                    enabled = !state.busy && state.preferencesReady && !state.preferencesFailed)
            }
            if (state.preferencesFailed) {
                Text(stringResource(R.string.settings_read_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetryPreferences, modifier = Modifier.testTag("backup_preferences_retry")) { Text(stringResource(R.string.transaction_retry)) }
            }
            if (state.preferences.lastAttemptFailed) Text(stringResource(R.string.backup_last_failed), color = MaterialTheme.colorScheme.error)
            state.preferences.lastSuccessMillis?.let { millis ->
                Text(stringResource(R.string.backup_last_success, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(millis))), style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(onClick = onLocalBackup, enabled = !state.busy, modifier = Modifier.fillMaxWidth().testTag("backup_local_now")) {
                Text(stringResource(R.string.backup_local_now))
            }
            if (state.history.isEmpty()) Text(stringResource(R.string.backup_history_empty), style = MaterialTheme.typography.bodySmall)
            state.history.forEach { entry ->
                Column(Modifier.fillMaxWidth().testTag("backup_history_${entry.fileName}"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BackupInfo(entry.metadata)
                    Text(stringResource(R.string.backup_history_size, (entry.sizeBytes + 1023) / 1024), style = MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onHistory(entry.fileName) }, enabled = !state.busy, modifier = Modifier.weight(1f).testTag("backup_history_restore_${entry.fileName}")) {
                            Text(stringResource(R.string.backup_restore))
                        }
                        TextButton(onClick = { selectedHistory = entry.fileName; exportPassword = true }, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.backup_history_export))
                        }
                        TextButton(onClick = { deletingHistory = entry.fileName }, enabled = !state.busy, modifier = Modifier.weight(1f).testTag("backup_history_delete_${entry.fileName}")) {
                            Text(stringResource(R.string.transaction_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    deletingHistory?.let { name ->
        AlertDialog(onDismissRequest = { deletingHistory = null }, title = { Text(stringResource(R.string.backup_delete_history)) },
            text = { Text(stringResource(R.string.backup_delete_history_hint)) },
            dismissButton = { TextButton(onClick = { deletingHistory = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { deletingHistory = null; onDeleteHistory(name) }, enabled = !state.busy,
                modifier = Modifier.testTag("backup_delete_history_confirm")) { Text(stringResource(R.string.transaction_delete)) } })
    }
    if (confirm && state.preview != null) AlertDialog(onDismissRequest = { if (!state.busy) confirm = false },
        title = { Text(stringResource(R.string.backup_confirm_title)) },
        text = { Text(stringResource(R.string.backup_confirm_message, state.preview.metadata.transactionCount)) },
        confirmButton = { TextButton(onClick = { confirm = false; onRestore() }, enabled = !state.busy, modifier = Modifier.testTag("backup_confirm")) { Text(stringResource(R.string.backup_confirm_action)) } },
        dismissButton = { TextButton(onClick = { confirm = false }, enabled = !state.busy, modifier = Modifier.testTag("backup_confirm_cancel")) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun BackupPasswordForm(unlock: Boolean, busy: Boolean, failed: Boolean,
    onDismiss: () -> Unit, onConfirm: (CharArray) -> Unit) {
    // Passwords intentionally never enter SavedStateHandle or rememberSaveable.
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val inView = remember { BringIntoViewRequester() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { inView.bringIntoView() }
    val valid = if (unlock) password.isNotEmpty() && password.length <= 128 else password.length in 8..128 && password == repeated
    Column(Modifier.fillMaxWidth().bringIntoViewRequester(inView), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(if (unlock) R.string.backup_unlock_title else R.string.backup_password_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (unlock) R.string.backup_unlock_hint else R.string.backup_password_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = password, onValueChange = { if (it.length <= 128) password = it }, enabled = !busy,
            label = { Text(stringResource(R.string.backup_password)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("backup_password"))
        if (!unlock) OutlinedTextField(value = repeated, onValueChange = { if (it.length <= 128) repeated = it }, enabled = !busy,
            label = { Text(stringResource(R.string.backup_password_repeat)) }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth().testTag("backup_password_repeat"))
        if (!unlock && repeated.isNotEmpty() && repeated != password) Text(stringResource(R.string.backup_password_mismatch), color = MaterialTheme.colorScheme.error)
        if (failed) Text(stringResource(R.string.backup_unlock_failed), color = MaterialTheme.colorScheme.error)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); onDismiss() }, enabled = !busy) { Text(stringResource(R.string.cancel)) }
            Button(onClick = {
                focus.clearFocus(); keyboard?.hide()
                val secret = password.toCharArray(); password = ""; repeated = ""; onConfirm(secret)
            }, enabled = valid && !busy, modifier = Modifier.testTag("backup_password_confirm")) {
                Text(stringResource(if (unlock) R.string.backup_unlock_action else R.string.backup_generate_encrypted))
            }
        }
    }
}

@Composable
private fun BackupInfo(metadata: BackupMetadata) {
    Text(stringResource(R.string.backup_info,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault()).format(metadata.createdAt),
        metadata.appVersion, metadata.transactionCount, metadata.activityCount), style = MaterialTheme.typography.bodyMedium)
}
