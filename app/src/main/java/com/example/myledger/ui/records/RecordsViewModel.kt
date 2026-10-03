package com.example.myledger.ui.records

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecordsViewModel(private val repository: LedgerRepository, private val savedState: SavedStateHandle) : ViewModel() {
    private val start = savedState.get<Long>("rangeStart")
    private val end = savedState.get<Long>("rangeEnd")
    private val initialRange = if (start != null && end != null) RecordDateRange(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end)) else null
    private val _state = MutableStateFlow(RecordsUiState(range = initialRange))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    init { reload() }

    fun setRange(range: RecordDateRange?) {
        savedState["rangeStart"] = range?.start?.toEpochDay()
        savedState["rangeEnd"] = range?.end?.toEpochDay()
        _state.update { it.copy(range = range, days = emptyList()) }
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        val range = _state.value.range
        val transactions = if (range == null) repository.observeTransactions() else repository.observeTransactions(range.start, range.end)
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                combine(transactions, repository.observeCategories(TransactionType.EXPENSE),
                    repository.observeCategories(TransactionType.INCOME), repository.observeCategories(TransactionType.REIMBURSEMENT),
                    repository.observeActivities(),
                ) { rows, expense, income, reimbursement, activities ->
                    RecordsGrouping.group(rows, expense + income + reimbursement, activities)
                }.flowOn(Dispatchers.Default).collect { days ->
                    _state.update { it.copy(days = days, isLoading = false, loadFailed = false) }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) { _state.update { it.copy(isLoading = false, loadFailed = true) } }
        }
    }
}
