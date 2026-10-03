package com.example.myledger.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.repository.ActivityDeleteResult
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ActivityEditorViewModel(
    private val repository: LedgerRepository,
    private val savedState: SavedStateHandle,
    private val activityId: Long? = null,
) : ViewModel() {
    private val initialForm = ActivityForm(
        name = savedState["name"] ?: "",
        type = savedState.get<String>("type")?.let(ActivityType::valueOf) ?: ActivityType.PERSONAL,
        startDate = savedState.get<Long>("start")?.let(LocalDate::ofEpochDay),
        endDate = savedState.get<Long>("end")?.let(LocalDate::ofEpochDay),
        note = savedState["note"] ?: "",
    )
    private val _state = MutableStateFlow(ActivityEditorUiState(initialForm, editingId = activityId,
        isLoading = activityId != null, savedId = savedState["savedId"],
        deleted = savedState["deleted"] ?: false, deleteUsage = savedState["deleteUsage"]))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init { reload() }

    fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                if (activityId != null && !_state.value.deleted && _state.value.savedId == null) {
                    val original = repository.getActivity(activityId)
                    if (original == null) {
                        _state.update { it.copy(isLoading = false, missing = true) }
                        return@launch
                    }
                    if (savedState.get<Boolean>("loaded") != true) {
                        val form = ActivityForm(original.name, original.type, original.startDate, original.endDate, original.note.orEmpty())
                        persist(form)
                        savedState["loaded"] = true
                        _state.update { it.copy(form = form) }
                    }
                }
                _state.update { it.copy(isLoading = false, missing = false) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    private fun edit(change: (ActivityForm) -> ActivityForm) {
        if (_state.value.busy || _state.value.missing || _state.value.loadFailed || _state.value.deleteUsage != null) return
        val form = change(_state.value.form)
        persist(form)
        _state.update { it.copy(form = form, nameError = false, saveFailed = false, deleteFailed = false) }
    }
    fun setName(value: String) = edit { it.copy(name = value) }
    fun setType(value: ActivityType) = edit { it.copy(type = value) }
    fun setStart(value: LocalDate?) = edit { it.copy(startDate = value) }
    fun setEnd(value: LocalDate?) = edit { it.copy(endDate = value) }
    fun setNote(value: String) = edit { it.copy(note = value) }

    fun save() {
        val current = _state.value
        if (current.busy || current.missing || current.loadFailed || current.deleteUsage != null) return
        if (current.form.name.isBlank()) { _state.update { it.copy(nameError = true) }; return }
        if (current.form.invalidDates) return
        _state.update { it.copy(isSaving = true, saveFailed = false, nameError = false) }
        viewModelScope.launch {
            try {
                val form = current.form
                val entity = ActivityEntity(activityId ?: 0L, form.name.trim(), form.type,
                    form.startDate, form.endDate, form.note.trim().takeIf(String::isNotEmpty))
                val id = if (activityId == null) repository.addActivity(entity) else {
                    repository.updateActivity(entity)
                    activityId
                }
                savedState["savedId"] = id
                _state.update { it.copy(isSaving = false, savedId = id) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isSaving = false, saveFailed = true) } }
        }
    }

    fun requestDelete() {
        val id = activityId ?: return
        val current = _state.value
        if (current.busy || current.missing || current.loadFailed || current.deleteUsage != null) return
        _state.update { it.copy(checkingDelete = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                if (repository.getActivity(id) == null) {
                    _state.update { it.copy(checkingDelete = false, missing = true) }
                } else {
                    val count = repository.countActivityTransactions(id)
                    savedState["deleteUsage"] = count
                    _state.update { it.copy(checkingDelete = false, deleteUsage = count) }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(checkingDelete = false, deleteFailed = true) } }
        }
    }

    fun dismissDelete() {
        if (_state.value.isDeleting) return
        savedState["deleteUsage"] = null
        _state.update { it.copy(deleteUsage = null) }
    }

    // Only a confirmed deletion of an apparently unused activity can reach this method.
    fun deleteConfirmed() {
        val id = activityId ?: return
        val current = _state.value
        if (current.busy || current.missing || current.loadFailed || current.deleteUsage != 0L) return
        _state.update { it.copy(isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                when (val result = repository.deleteActivityIfUnused(id)) {
                    ActivityDeleteResult.Deleted, ActivityDeleteResult.Missing -> {
                        savedState["deleted"] = true
                        savedState["deleteUsage"] = null
                        _state.update { it.copy(isDeleting = false, deleteUsage = null, deleted = true) }
                    }
                    is ActivityDeleteResult.InUse -> {
                        savedState["deleteUsage"] = result.count
                        _state.update { it.copy(isDeleting = false, deleteUsage = result.count) }
                    }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isDeleting = false, deleteFailed = true) } }
        }
    }

    private fun persist(form: ActivityForm) {
        savedState["name"] = form.name
        savedState["type"] = form.type.name
        savedState["start"] = form.startDate?.toEpochDay()
        savedState["end"] = form.endDate?.toEpochDay()
        savedState["note"] = form.note
    }
}
