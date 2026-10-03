package com.example.myledger.analysis.model

import java.time.LocalDate

data class PeriodSummary(
    val start: LocalDate,
    val end: LocalDate,
    val expense: ExpenseSummary,
    val income: IncomeSummary,
    val dailyCategories: List<CategoryBreakdown>,
    val allCategories: List<CategoryBreakdown>,
)
