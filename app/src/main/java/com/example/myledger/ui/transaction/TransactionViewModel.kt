package com.example.myledger.ui.transaction

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TransactionViewModel(
    private val repository: LedgerRepository,
    private val savedState: SavedStateHandle,
    today: LocalDate = LocalDate.now(),
    private val transactionId: Long? = null,
) : ViewModel() {
    private val initialForm = TransactionForm(
        type = savedState.get<String>("type")?.let(TransactionType::valueOf) ?: TransactionType.EXPENSE,
        amount = savedState["amount"] ?: "",
        categoryId = savedState["categoryId"], activityId = savedState["activityId"],
        reimbursable = savedState["reimbursable"] ?: false,
        date = savedState.get<Long>("date")?.let(LocalDate::ofEpochDay) ?: today,
        note = savedState["note"] ?: "",
    )
    private val _state = MutableStateFlow(TransactionUiState(initialForm, saved = restoredReceipt(),
        editingId = transactionId, deleted = savedState["deleted"] ?: false))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        if (transactionId == null || savedState.get<Boolean>("editLoaded") == true) persist(initialForm)
        reload()
    }

    private fun restoredReceipt(): SavedTransaction? {
        val id = savedState.get<Long>("savedId") ?: return null
        val amount = MoneyInput.parseMinor(initialForm.amount) ?: return null
        return SavedTransaction(id, initialForm.type, amount, savedState["savedCategoryName"] ?: "", initialForm.date)
    }

    fun reload() {
        loadJob?.cancel()
        _state.update { it.copy(isLoading = true, loadFailed = false, missingRecord = false) }
        loadJob = viewModelScope.launch {
            try {
                if (transactionId != null && savedState.get<Boolean>("editLoaded") != true && !_state.value.deleted) {
                    val original = repository.getTransaction(transactionId)
                    if (original == null) {
                        _state.update { it.copy(isLoading = false, missingRecord = true) }
                        return@launch
                    }
                    val form = TransactionForm(type = original.type, amount = MoneyInput.formatMinor(original.amountMinor),
                        categoryId = original.categoryId, activityId = original.activityId,
                        reimbursable = original.reimbursable, date = original.date, note = original.note.orEmpty())
                    persist(form)
                    savedState["editLoaded"] = true
                    _state.update { it.copy(form = form) }
                }
                combine(
                    repository.observeCategories(TransactionType.EXPENSE),
                    repository.observeCategories(TransactionType.INCOME),
                    repository.observeCategories(TransactionType.REIMBURSEMENT),
                    repository.observeActivities(),
                ) { expense, income, reimbursement, activities ->
                    (expense + income + reimbursement) to activities
                }.collect { (categories, activities) ->
                    _state.update { it.copy(allCategories = categories, activities = activities, isLoading = false, loadFailed = false) }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }

    private fun edit(change: (TransactionForm) -> TransactionForm) {
        if (_state.value.isSaving || _state.value.isDeleting || _state.value.saved != null || _state.value.deleted ||
            _state.value.missingRecord || (transactionId != null && _state.value.isLoading)) return
        val form = change(_state.value.form)
        persist(form)
        _state.update { it.copy(form = form, amountError = false, saveFailed = false, deleteFailed = false) }
    }

    fun setType(type: TransactionType) = edit {
        if (it.type == type) it else it.copy(type = type, categoryId = null,
            reimbursable = if (type == TransactionType.EXPENSE) it.reimbursable else false)
    }
    fun setAmount(amount: String) = edit { it.copy(amount = amount) }
    fun setCategory(id: Long) {
        if (_state.value.categories.any { it.id == id }) edit { it.copy(categoryId = id) }
    }
    fun setActivity(id: Long?) {
        if (id == null || _state.value.activities.any { it.id == id }) edit { it.copy(activityId = id) }
    }
    fun setReimbursable(value: Boolean) = edit { it.copy(reimbursable = value && it.type == TransactionType.EXPENSE) }
    fun setDate(date: LocalDate) = edit { it.copy(date = date) }
    fun setNote(note: String) = edit { it.copy(note = note) }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.isDeleting || current.saved != null || current.deleted || current.missingRecord ||
            current.isLoading || current.loadFailed) return
        val amount = MoneyInput.parseMinor(current.form.amount)
        if (amount == null) { _state.update { it.copy(amountError = true) }; return }
        val category = current.selectedCategory ?: return
        _state.update { it.copy(isSaving = true, amountError = false, saveFailed = false) }
        viewModelScope.launch {
            try {
                val entity = TransactionEntity(
                    id = transactionId ?: 0L,
                    type = current.form.type, amountMinor = amount, categoryId = category.id,
                    activityId = current.form.activityId, reimbursable = current.form.reimbursable,
                    date = current.form.date, note = current.form.note.trim().takeIf { it.isNotEmpty() },
                )
                val id = if (transactionId == null) repository.addTransaction(entity) else {
                    repository.updateTransaction(entity)
                    transactionId
                }
                savedState["savedCategoryName"] = category.name
                savedState["savedId"] = id
                val receipt = SavedTransaction(id, current.form.type, amount, category.name, current.form.date)
                _state.update { it.copy(isSaving = false, saved = receipt) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isSaving = false, saveFailed = true) } }
        }
    }

    // Called only by the confirmation dialog; this action is not triggered by opening the editor.
    fun deleteConfirmed() {
        val id = transactionId ?: return
        val current = _state.value
        if (current.isSaving || current.isDeleting || current.saved != null || current.deleted || current.missingRecord || current.isLoading) return
        _state.update { it.copy(isDeleting = true, deleteFailed = false) }
        viewModelScope.launch {
            try {
                repository.deleteTransaction(id)
                savedState["deleted"] = true
                _state.update { it.copy(isDeleting = false, deleted = true) }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isDeleting = false, deleteFailed = true) } }
        }
    }

    private fun persist(form: TransactionForm) {
        savedState["type"] = form.type.name
        savedState["amount"] = form.amount
        savedState["categoryId"] = form.categoryId
        savedState["activityId"] = form.activityId
        savedState["reimbursable"] = form.reimbursable
        savedState["date"] = form.date.toEpochDay()
        savedState["note"] = form.note
    }
}
