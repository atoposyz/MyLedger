package com.example.myledger.analysis.model

import java.time.YearMonth

data class MonthlyExpense(val month: YearMonth, val amountMinor: Long?)

data class StatisticsSummary(
    val month: YearMonth,
    val scope: ExpenseScope,
    val totalMinor: Long?,
    val categories: List<CategoryBreakdown>,
    val trend: List<MonthlyExpense>,
    val trendMaximumMinor: Long,
)
