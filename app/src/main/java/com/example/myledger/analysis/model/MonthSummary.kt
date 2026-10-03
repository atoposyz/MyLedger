package com.example.myledger.analysis.model

import java.time.YearMonth

data class ActivityExpenseSummary(val activityId: Long, val amountMinor: Long?, val recordCount: Int)

data class MonthSummary(
    val month: YearMonth,
    val recordCount: Int,
    val period: PeriodSummary?,
    val balanceMinor: Long?,
    val activityExpenses: List<ActivityExpenseSummary>,
)
