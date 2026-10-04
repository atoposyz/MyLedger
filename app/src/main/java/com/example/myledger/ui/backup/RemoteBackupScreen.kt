package com.example.myledger.ui.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.backup.RemoteBackup

@Composable fun RemoteBackupRoute(onConfigure: () -> Unit, onBusyChanged: (Boolean) -> Unit) {
    val app = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(app) { viewModelFactory { initializer { RemoteBackupViewModel(app.remoteBackups, app.backupManager, app.integrationSettings, createSavedStateHandle()) } } }
    val vm: RemoteBackupViewModel = viewModel(factory = factory); val state by vm.state.collectAsStateWithLifecycle()
    SideEffect { onBusyChanged(state.busy) }; DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }; BackHandler(state.busy) {}
    RemoteBackupScreen(state, onConfigure, vm::refresh, vm::upload, vm::download, vm::unlock, vm::cancel, vm::restoreConfirmed, vm::deleteConfirmed)
}
@Composable fun RemoteBackupScreen(state: RemoteBackupUiState, onConfigure: () -> Unit, onRefresh: () -> Unit, onUpload: (CharArray) -> Unit,
    onDownload: (RemoteBackup) -> Unit, onUnlock: (CharArray) -> Unit, onCancel: () -> Unit, onRestore: () -> Unit, onDelete: (RemoteBackup) -> Unit) {
    var uploading by remember { mutableStateOf(false) }; var confirm by remember { mutableStateOf(false) }; var deleting by remember { mutableStateOf<RemoteBackup?>(null) }
    LaunchedEffect(state.preview?.fileName) { confirm = false }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("服务器仅保存密码加密文件。手动上传与恢复，不同步日常账目；备份密码由你保管。", style = MaterialTheme.typography.bodySmall)
            if (!state.configured) Text("尚未配置服务器；本地记账和本地备份可照常使用。", modifier = Modifier.testTag("remote_unconfigured"))
            OutlinedButton(onClick = onConfigure, enabled = !state.busy, modifier = Modifier.testTag("remote_configure")) { Text("连接配置") }
            Button(onClick = { uploading = true }, enabled = state.ready && state.configured && !state.busy, modifier = Modifier.testTag("remote_upload")) { Text("加密并上传当前账本") }
            if (uploading) BackupPasswordForm(false, state.busy, false, { uploading = false }, { uploading = false; onUpload(it) })
            OutlinedButton(onRefresh, enabled = state.ready && state.configured && !state.busy, modifier = Modifier.testTag("remote_refresh")) { Text("连接并刷新备份列表") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, modifier = Modifier.testTag("remote_message")) }
            state.locked?.let { key(it) { BackupPasswordForm(true, state.busy, state.message != null, onCancel, onUnlock) } }
            state.preview?.let { preview ->
                BackupInfo(preview.metadata)
                Text("确认后替换当前账本；恢复前会保存一份本地副本。")
                Button({ confirm = true }, enabled = !state.busy, modifier = Modifier.testTag("remote_restore")) { Text("恢复此备份") }
                TextButton(onCancel, enabled = !state.busy) { Text("取消恢复") }
            }
            if (state.loaded && state.entries.isEmpty()) Text("服务器暂无备份")
            state.entries.forEach { entry ->
                HorizontalDivider(); Text(entry.createdAt.toString()); Text("${(entry.sizeBytes + 1023) / 1024} KiB · ${entry.id.take(8)}", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton({ onDownload(entry) }, enabled = !state.busy, modifier = Modifier.testTag("remote_download_${entry.id}")) { Text("下载并预览") }
                    TextButton({ deleting = entry }, enabled = !state.busy, modifier = Modifier.testTag("remote_delete_${entry.id}")) { Text("删除服务器备份", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if (confirm && state.preview != null) AlertDialog(onDismissRequest = { if (!state.busy) confirm = false }, title = { Text("确认替换当前账本？") },
        text = { Text("所有现有账目和活动会被备份替换。") }, dismissButton = { TextButton({ confirm = false }, enabled = !state.busy) { Text("取消") } },
        confirmButton = { TextButton({ confirm = false; onRestore() }, enabled = !state.busy, modifier = Modifier.testTag("remote_restore_confirm")) { Text("确认恢复") } })
    deleting?.let { entry -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("删除服务器上的这份备份？") }, text = { Text("当前账本和本地历史不会被删除。") },
        dismissButton = { TextButton({ deleting = null }) { Text("取消") } }, confirmButton = { TextButton({ deleting = null; onDelete(entry) }, enabled = !state.busy,
            modifier = Modifier.testTag("remote_delete_confirm")) { Text("确认删除") } }) }
}
