package com.example.myledger.analysis.model

data class ExpenseSummary(
    val totalMinor: Long,
    val dailyMinor: Long,
    val activityMinor: Long,
    val reimbursableMinor: Long,
)

enum class ExpenseScope { DAILY, ALL }
