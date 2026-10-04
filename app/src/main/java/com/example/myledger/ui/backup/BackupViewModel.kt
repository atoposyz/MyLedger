package com.example.myledger.ui.backup

import android.net.Uri
import androidx.lifecycle.*
import com.example.myledger.backup.*
import com.example.myledger.data.settings.BackupPreferences
import com.example.myledger.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class BackupMessage { SAVED, RESTORED, INVALID_FILE, EXPORT_FAILED, IMPORT_FAILED, RESTORE_FAILED, SAVE_FAILED, SHARE_FAILED, FILE_EXPIRED,
    UNLOCK_FAILED, HISTORY_FAILED, LOCAL_SAVED, AUTO_FAILED, HISTORY_DELETED }
data class BackupUiState(val busy: Boolean = false, val export: PreparedBackup? = null, val preview: PreparedBackup? = null,
    val hasSafetyCopy: Boolean = false, val message: BackupMessage? = null, val restoredCount: Int = 0,
    val lockedImport: String? = null, val preferences: BackupPreferences = BackupPreferences(), val preferencesReady: Boolean = false,
    val preferencesFailed: Boolean = false, val history: List<HistoryBackup> = emptyList())

class BackupViewModel(private val manager: BackupManager, private val savedState: SavedStateHandle,
    private val settings: SettingsRepository, private val scheduler: LocalBackupScheduler) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState(hasSafetyCopy = manager.hasSafetyCopy))
    val state = _state.asStateFlow()
    private var preferencesJob: Job? = null
    fun reloadPreferences() {
        preferencesJob?.cancel()
        _state.update { it.copy(preferencesReady = false, preferencesFailed = false) }
        preferencesJob = viewModelScope.launch {
            try { settings.backupPreferences.collect { preferences -> _state.update { it.copy(preferences = preferences, preferencesReady = true, preferencesFailed = false) } } }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { _state.update { it.copy(preferencesFailed = true) } }
        }
    }
    init {
        reloadPreferences()
        viewModelScope.launch { manager.history.collect { entries -> _state.update { it.copy(history = entries) } } }
        val pending = savedState.get<String>("pendingImport")
        val exported = savedState.get<String>("preparedExport")
        val locked = savedState.get<String>("lockedImport")
        operation(if (pending == null && exported == null && locked == null) BackupMessage.HISTORY_FAILED else BackupMessage.FILE_EXPIRED) {
            manager.refreshHistory()
            if (pending != null) _state.update { it.copy(preview = manager.readPreview(pending)) }
            if (exported != null) _state.update { it.copy(export = manager.readExportPreview(exported)) }
            if (locked != null) {
                require(manager.hasLockedImport(locked)) { "导入文件已失效" }
                _state.update { it.copy(lockedImport = locked) }
            }
        }
    }
    private fun operation(failure: BackupMessage, secret: CharArray? = null, action: suspend () -> Unit) {
        if (_state.value.busy) { secret?.fill('\u0000'); return }
        _state.update { it.copy(busy = true, message = null) }
        val job = viewModelScope.launch {
            try { action()
            } catch (error: CancellationException) { throw error
            } catch (error: BackupPasswordRequired) {
                savedState["lockedImport"] = error.fileName; _state.update { it.copy(lockedImport = error.fileName) }
            } catch (error: Exception) { _state.update { it.copy(message = if (error is BackupFormatException) BackupMessage.INVALID_FILE else failure) }
            } finally { _state.update { it.copy(busy = false, hasSafetyCopy = manager.hasSafetyCopy) } }
        }
        job.invokeOnCompletion { secret?.fill('\u0000') }
    }
    fun prepareExport() = operation(BackupMessage.EXPORT_FAILED) {
        val export = manager.prepareExport(); savedState["preparedExport"] = export.fileName
        _state.update { it.copy(export = export) }
    }
    fun encryptedExport(password: CharArray, historyName: String? = null) = operation(BackupMessage.EXPORT_FAILED, password) {
        val export = manager.prepareEncryptedExport(password, historyName); savedState["preparedExport"] = export.fileName
        _state.update { it.copy(export = export) }
    }
    fun saveExport(uri: Uri) {
        val export = _state.value.export ?: return
        operation(BackupMessage.SAVE_FAILED) { manager.saveExport(export.fileName, uri); _state.update { it.copy(message = BackupMessage.SAVED) } }
    }
    fun import(uri: Uri) = operation(BackupMessage.IMPORT_FAILED) {
        clearSelection(); setPreview(manager.prepareImport(uri))
    }
    fun unlock(password: CharArray) = operation(BackupMessage.UNLOCK_FAILED, password) {
        val name = requireNotNull(_state.value.lockedImport)
        setPreview(manager.unlockImport(name, password)); savedState["lockedImport"] = null
        _state.update { it.copy(lockedImport = null) }
    }
    fun cancelUnlock() { if (!_state.value.busy) clearSelection() }
    private fun clearSelection() {
        _state.value.preview?.let { manager.discardImport(it.fileName) }
        _state.value.lockedImport?.let { manager.discardLockedImport(it) }
        savedState["pendingImport"] = null; savedState["lockedImport"] = null
        _state.update { it.copy(preview = null, lockedImport = null, message = null) }
    }
    fun localBackup() = operation(BackupMessage.HISTORY_FAILED) {
        manager.createLocalBackup(); _state.update { it.copy(message = BackupMessage.LOCAL_SAVED) }
    }
    fun setAutomatic(enabled: Boolean) = operation(BackupMessage.AUTO_FAILED) {
        val previous = settings.currentBackupPreferences().automatic
        if (enabled && !previous) manager.createLocalBackup()
        withContext(NonCancellable) {
            settings.setAutomaticBackup(enabled)
            try { scheduler.sync(enabled) } catch (error: Exception) {
                settings.setAutomaticBackup(previous)
                runCatching { scheduler.sync(previous) }
                throw error
            }
        }
    }
    fun historyPreview(name: String) = operation(BackupMessage.HISTORY_FAILED) { setPreview(manager.prepareHistoryImport(name)) }
    fun deleteHistory(name: String) = operation(BackupMessage.HISTORY_FAILED) {
        manager.deleteHistory(name); _state.update { it.copy(message = BackupMessage.HISTORY_DELETED) }
    }
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
