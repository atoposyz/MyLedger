package com.example.myledger.ui.backup

import androidx.lifecycle.*
import com.example.myledger.backup.*
import com.example.myledger.data.settings.IntegrationSettingsRepository
import com.example.myledger.network.ServiceException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteBackupUiState(val configured: Boolean = false, val ready: Boolean = false, val busy: Boolean = false,
    val loaded: Boolean = false, val entries: List<RemoteBackup> = emptyList(), val message: String? = null,
    val locked: String? = null, val preview: PreparedBackup? = null)
class RemoteBackupViewModel(private val remote: RemoteBackupRepository, private val manager: BackupManager,
    private val config: IntegrationSettingsRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutable = MutableStateFlow(RemoteBackupUiState()); val state = mutable.asStateFlow()
    init {
        viewModelScope.launch {
            try { config.settings.collect { value -> mutable.update { it.copy(configured = value.hasServerToken && value.serverUrl.isNotBlank(), ready = true) } } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.update { it.copy(message = "无法读取配置") } }
        }
        operation {
            saved.get<String>("remoteLocked")?.let { name -> require(manager.hasLockedImport(name)); mutable.update { it.copy(locked = name) } }
            saved.get<String>("remotePreview")?.let { name -> mutable.update { it.copy(preview = manager.readPreview(name)) } }
        }
    }
    private fun operation(secret: CharArray? = null, action: suspend () -> Unit) {
        if (state.value.busy) { secret?.fill('\u0000'); return }; mutable.update { it.copy(busy = true, message = null) }
        val job = viewModelScope.launch {
            try { action() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(message = when(error) {
                is ServiceException -> error.message
                is BackupUnlockException -> "密码错误或备份已损坏"
                else -> "操作失败，请检查网络、配置或备份文件后重试；账本不会因下载失败被替换。"
            }) } }
            finally { mutable.update { it.copy(busy = false) } }
        }; job.invokeOnCompletion { secret?.fill('\u0000') }
    }
    fun refresh() = operation { mutable.update { it.copy(entries = remote.list(), loaded = true) } }
    fun upload(password: CharArray) = operation(password) {
        remote.upload(password); mutable.update { it.copy(message = "加密备份上传成功") }
        try { mutable.update { it.copy(entries = remote.list(), loaded = true) } } catch (_: Exception) { mutable.update { it.copy(message = "上传成功，列表刷新失败，请手动刷新") } }
    }
    fun download(entry: RemoteBackup) = operation {
        clearSelection(); val name = remote.download(entry); saved["remoteLocked"] = name; mutable.update { it.copy(locked = name) }
    }
    fun unlock(password: CharArray) = operation(password) {
        val preview = manager.unlockImport(requireNotNull(state.value.locked), password)
        saved["remoteLocked"] = null; saved["remotePreview"] = preview.fileName
        mutable.update { it.copy(locked = null, preview = preview) }
    }
    fun cancel() { if (!state.value.busy) clearSelection() }
    private fun clearSelection() {
        state.value.locked?.let(manager::discardLockedImport); state.value.preview?.let { manager.discardImport(it.fileName) }
        saved["remoteLocked"] = null; saved["remotePreview"] = null; mutable.update { it.copy(locked = null, preview = null) }
    }
    fun restoreConfirmed() = operation {
        manager.restore(requireNotNull(state.value.preview).fileName); clearSelection(); mutable.update { it.copy(message = "服务器备份恢复成功；可在本地备份页恢复替换前账本") }
    }
    fun deleteConfirmed(entry: RemoteBackup) = operation {
        remote.delete(entry); mutable.update { it.copy(entries = it.entries.filterNot { row -> row.id == entry.id }, message = "服务器备份已删除") }
    }
}
