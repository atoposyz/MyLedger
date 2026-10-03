package com.example.myledger.ui.statistics

import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.analysis.model.StatisticsSummary
import java.time.YearMonth

data class StatisticsUiState(
    val month: YearMonth = YearMonth.now(),
    val scope: ExpenseScope = ExpenseScope.DAILY,
    val summary: StatisticsSummary? = null,
    val categoryNames: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)
