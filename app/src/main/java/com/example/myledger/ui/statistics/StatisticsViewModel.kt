package com.example.myledger.ui.statistics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
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

class StatisticsViewModel(private val repository: LedgerRepository, private val savedState: SavedStateHandle,
    private val today: () -> LocalDate = { LocalDate.now() }) : ViewModel() {
    private val initialScope = ExpenseScope.entries.firstOrNull { it.name == savedState.get<String>("expenseScope") } ?: ExpenseScope.DAILY
    private val _state = MutableStateFlow(StatisticsUiState(month = YearMonth.from(today()), scope = initialScope))
    val state = _state.asStateFlow()
    private var loadJob: Job? = null
    init { reload() }

    fun setScope(scope: ExpenseScope) {
        if (scope == _state.value.scope) return
        savedState["expenseScope"] = scope.name
        _state.update { StatisticsUiState(month = it.month, scope = scope) }
        reload()
    }

    fun refreshMonth() {
        val month = YearMonth.from(today())
        if (month != _state.value.month) {
            _state.update { StatisticsUiState(month = month, scope = it.scope) }
            reload()
        }
    }

    fun reload() {
        loadJob?.cancel()
        val month = _state.value.month
        val scope = _state.value.scope
        _state.update { it.copy(isLoading = true, loadFailed = false) }
        loadJob = viewModelScope.launch {
            try {
                combine(repository.observeTransactions(), repository.observeCategories(TransactionType.EXPENSE)) { rows, categories ->
                    StatisticsUiState(month, scope, FinancialAnalysis.summarizeStatistics(rows, month, scope),
                        categories.associate { it.id to it.name }, isLoading = false)
                }.flowOn(Dispatchers.Default).collect { next ->
                    _state.update { if (it.month == month && it.scope == scope) next else it }
                }
            } catch (error: CancellationException) { throw error
            } catch (_: Exception) {
                _state.update { if (it.month == month && it.scope == scope) it.copy(isLoading = false, loadFailed = true) else it }
            }
        }
    }
}
