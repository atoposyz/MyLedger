package com.example.myledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.ui.records.RecordItem
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: LedgerRepository, private val today: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState(month = YearMonth.from(today())))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    init { reload() }

    fun refreshMonth() {
        val month = YearMonth.from(today())
        if (month != _state.value.month) {
            _state.value = HomeUiState(month = month)
            reload()
        }
    }

    fun reload() {
        loadJob?.cancel()
        val month = _state.value.month
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                val visibleRows = combine(repository.observeTransactions(month.atDay(1), month.atEndOfMonth()),
                    repository.observeRecentTransactions()) { monthly, recent -> monthly to recent }
                combine(visibleRows, repository.observeCategories(TransactionType.EXPENSE),
                    repository.observeCategories(TransactionType.INCOME), repository.observeCategories(TransactionType.REIMBURSEMENT),
                    repository.observeActivities(),
                ) { (rows, recentRows), expense, income, reimbursement, activities ->
                    val categoryNames = (expense + income + reimbursement).associate { it.id to it.name }
                    val activityById = activities.associateBy { it.id }
                    val recent = recentRows.map { RecordItem(it, categoryNames[it.categoryId], activityById[it.activityId]?.name) }
                    HomeUiState(month, FinancialAnalysis.summarizeMonth(rows, month), activityById, recent, isLoading = false)
                }.flowOn(Dispatchers.Default).collect { next ->
                    _state.update { if (it.month == month) next else it }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                _state.update { if (it.month == month) it.copy(isLoading = false, loadFailed = true) else it }
            }
        }
    }
}
