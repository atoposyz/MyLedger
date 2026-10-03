package com.example.myledger.ui.backup

import android.net.Uri
import androidx.lifecycle.*
import com.example.myledger.backup.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupMessage { SAVED, RESTORED, INVALID_FILE, EXPORT_FAILED, IMPORT_FAILED, RESTORE_FAILED, SAVE_FAILED, SHARE_FAILED, FILE_EXPIRED }
data class BackupUiState(val busy: Boolean = false, val export: PreparedBackup? = null, val preview: PreparedBackup? = null,
    val hasSafetyCopy: Boolean = false, val message: BackupMessage? = null, val restoredCount: Int = 0)

class BackupViewModel(private val manager: BackupManager, private val savedState: SavedStateHandle) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState(hasSafetyCopy = manager.hasSafetyCopy))
    val state = _state.asStateFlow()
    init {
        val pending = savedState.get<String>("pendingImport")
        val exported = savedState.get<String>("preparedExport")
        if (pending != null || exported != null) operation(BackupMessage.FILE_EXPIRED) {
            if (pending != null) _state.update { it.copy(preview = manager.readPreview(pending)) }
            if (exported != null) _state.update { it.copy(export = manager.readExportPreview(exported)) }
        }
    }
    private fun operation(failure: BackupMessage, action: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try { action()
            } catch (error: CancellationException) { throw error
            } catch (error: Exception) { _state.update { it.copy(message = if (error is BackupFormatException) BackupMessage.INVALID_FILE else failure) }
            } finally { _state.update { it.copy(busy = false, hasSafetyCopy = manager.hasSafetyCopy) } }
        }
    }
    fun prepareExport() = operation(BackupMessage.EXPORT_FAILED) {
        val export = manager.prepareExport(); savedState["preparedExport"] = export.fileName
        _state.update { it.copy(export = export) }
    }
    fun saveExport(uri: Uri) {
        val export = _state.value.export ?: return
        operation(BackupMessage.SAVE_FAILED) { manager.saveExport(export.fileName, uri); _state.update { it.copy(message = BackupMessage.SAVED) } }
    }
    fun import(uri: Uri) = operation(BackupMessage.IMPORT_FAILED) { setPreview(manager.prepareImport(uri)) }
    fun importSafetyCopy() = operation(BackupMessage.IMPORT_FAILED) { setPreview(manager.prepareSafetyImport()) }
    private fun setPreview(preview: PreparedBackup) {
        _state.value.preview?.let { manager.discardImport(it.fileName) }
        savedState["pendingImport"] = preview.fileName
        _state.update { it.copy(preview = preview) }
    }
    fun cancelPreview() {
        if (_state.value.busy) return
        _state.value.preview?.let { manager.discardImport(it.fileName) }
        savedState["pendingImport"] = null; _state.update { it.copy(preview = null, message = null) }
    }
    fun restoreConfirmed() {
        val preview = _state.value.preview ?: return
        operation(BackupMessage.RESTORE_FAILED) {
            val metadata = manager.restore(preview.fileName)
            manager.discardImport(preview.fileName); savedState["pendingImport"] = null
            _state.update { it.copy(preview = null, message = BackupMessage.RESTORED, restoredCount = metadata.transactionCount) }
        }
    }
    fun exportFile() = manager.exportFile(requireNotNull(_state.value.export).fileName)
    fun shareFailed() { _state.update { it.copy(message = BackupMessage.SHARE_FAILED) } }
}
