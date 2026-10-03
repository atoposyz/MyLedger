package com.example.myledger.ui.batchentry

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.util.MoneyInput
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class BatchEntryViewModel(
    private val repository: LedgerRepository,
    private val savedState: SavedStateHandle,
    today: LocalDate = LocalDate.now(),
) : ViewModel() {
    private val initialDefaults = BatchDefaults(
        date = savedState.get<Long>("defaultDate")?.let(LocalDate::ofEpochDay) ?: today,
        activityId = savedState["defaultActivity"], reimbursable = savedState["defaultReimbursable"] ?: false,
    )
    private val restoredDrafts = savedState.get<ArrayList<Bundle>>("drafts")?.map { bundle ->
        TransactionDraft(
            id = bundle.getLong("id"), type = TransactionType.valueOf(requireNotNull(bundle.getString("type"))),
            amount = bundle.getString("amount").orEmpty(), categoryId = bundle.optionalId("category"),
            date = LocalDate.ofEpochDay(bundle.getLong("date")), activityId = bundle.optionalId("activity"),
            reimbursable = bundle.getBoolean("reimbursable"), note = bundle.getString("note").orEmpty(),
        )
    } ?: listOf(TransactionDraft(id = 1, date = initialDefaults.date))
    private var nextId = (restoredDrafts.maxOfOrNull { it.id } ?: 0L) + 1L
    private val restoredSaved = savedState.get<Int>("savedCount")?.let {
        SavedBatch(it, requireNotNull(savedState.get<Long>("savedTotal")))
    }
    private val _state = MutableStateFlow(BatchEntryUiState(initialDefaults, restoredDrafts, saved = restoredSaved))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init { persist(); reload() }

    fun reload() {
        loadJob?.cancel()
        _state.value = _state.value.copy(isLoading = true, loadFailed = false)
        loadJob = viewModelScope.launch {
            try {
                combine(repository.observeCategories(TransactionType.EXPENSE),
                    repository.observeCategories(TransactionType.INCOME),
                    repository.observeCategories(TransactionType.REIMBURSEMENT), repository.observeActivities(),
                ) { expense, income, reimbursement, activities -> (expense + income + reimbursement) to activities }
                    .collect { (categories, activities) ->
                        _state.value = _state.value.copy(categories = categories, activities = activities,
                            isLoading = false, loadFailed = false)
                    }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.value = _state.value.copy(isLoading = false, loadFailed = true) }
        }
    }

    private fun edit(change: (BatchEntryUiState) -> BatchEntryUiState) {
        if (_state.value.isSaving || _state.value.saved != null) return
        _state.value = change(_state.value).copy(saveFailed = false)
        persist()
    }

    fun setDefaultDate(date: LocalDate) = edit { it.copy(defaults = it.defaults.copy(date = date)) }
    fun setDefaultActivity(id: Long?) {
        if (id == null || _state.value.activities.any { it.id == id }) edit {
            it.copy(defaults = it.defaults.copy(activityId = id, reimbursable = false))
        }
    }
    fun setDefaultReimbursable(value: Boolean) = edit {
        it.copy(defaults = it.defaults.copy(reimbursable = value && it.defaultIsWork))
    }

    fun addDraft() = edit { current ->
        val previous = current.drafts.lastOrNull()
        val type = previous?.type ?: TransactionType.EXPENSE
        val draft = TransactionDraft(id = nextId++, type = type,
            categoryId = previous?.let { current.selectedCategory(it)?.id }, date = current.defaults.date,
            activityId = current.defaults.activityId,
            reimbursable = type == TransactionType.EXPENSE && current.defaultIsWork && current.defaults.reimbursable)
        current.copy(drafts = current.drafts + draft)
    }

    fun removeDraft(id: Long) = edit { it.copy(drafts = it.drafts.filterNot { draft -> draft.id == id },
        invalidAmountIds = it.invalidAmountIds - id) }

    private fun editDraft(id: Long, change: (TransactionDraft) -> TransactionDraft) = edit {
        it.copy(drafts = it.drafts.map { draft -> if (draft.id == id) change(draft) else draft },
            invalidAmountIds = it.invalidAmountIds - id)
    }
    fun setType(id: Long, type: TransactionType) = editDraft(id) {
        if (it.type == type) it else it.copy(type = type, categoryId = null,
            reimbursable = if (type == TransactionType.EXPENSE) it.reimbursable else false)
    }
    fun setAmount(id: Long, amount: String) = editDraft(id) { it.copy(amount = amount) }
    fun setNote(id: Long, note: String) = editDraft(id) { it.copy(note = note) }
    fun setDate(id: Long, date: LocalDate) = editDraft(id) { it.copy(date = date) }
    fun setCategory(id: Long, categoryId: Long) {
        val draft = _state.value.drafts.find { it.id == id } ?: return
        if (_state.value.categories.any { it.id == categoryId && it.type == draft.type })
            editDraft(id) { it.copy(categoryId = categoryId) }
    }
    fun setActivity(id: Long, activityId: Long?) {
        if (activityId == null || _state.value.activities.any { it.id == activityId })
            editDraft(id) { it.copy(activityId = activityId) }
    }
    fun setReimbursable(id: Long, value: Boolean) = editDraft(id) {
        it.copy(reimbursable = value && it.type == TransactionType.EXPENSE)
    }

    fun saveAll() {
        val current = _state.value
        if (current.isSaving || current.saved != null || current.isLoading || current.loadFailed || current.drafts.isEmpty()) return
        val invalid = current.drafts.filter { MoneyInput.parseMinor(it.amount) == null }.map { it.id }.toSet()
        if (invalid.isNotEmpty()) { _state.value = current.copy(invalidAmountIds = invalid); return }
        val total = current.totalMinor ?: return
        if (current.drafts.any { current.selectedCategory(it) == null }) return
        val items = current.drafts.map { draft ->
            TransactionEntity(type = draft.type, amountMinor = requireNotNull(MoneyInput.parseMinor(draft.amount)),
                categoryId = requireNotNull(current.selectedCategory(draft)).id, activityId = draft.activityId,
                date = draft.date, reimbursable = draft.reimbursable,
                note = draft.note.trim().takeIf { it.isNotEmpty() })
        }
        _state.value = current.copy(isSaving = true, invalidAmountIds = emptySet(), saveFailed = false)
        viewModelScope.launch {
            try {
                repository.addTransactions(items)
                savedState["savedTotal"] = total
                savedState["savedCount"] = items.size
                _state.value = _state.value.copy(isSaving = false, saved = SavedBatch(items.size, total))
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.value = _state.value.copy(isSaving = false, saveFailed = true) }
        }
    }

    private fun persist() {
        val current = _state.value
        savedState["defaultDate"] = current.defaults.date.toEpochDay()
        savedState["defaultActivity"] = current.defaults.activityId
        savedState["defaultReimbursable"] = current.defaults.reimbursable
        savedState["drafts"] = ArrayList(current.drafts.map { draft -> Bundle().apply {
            putLong("id", draft.id); putString("type", draft.type.name); putString("amount", draft.amount)
            draft.categoryId?.let { putLong("category", it) }; putLong("date", draft.date.toEpochDay())
            draft.activityId?.let { putLong("activity", it) }; putBoolean("reimbursable", draft.reimbursable)
            putString("note", draft.note)
        } })
    }
}

private fun Bundle.optionalId(key: String): Long? = if (containsKey(key)) getLong(key) else null
