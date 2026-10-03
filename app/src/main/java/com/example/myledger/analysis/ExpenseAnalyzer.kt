package com.example.myledger.analysis

import com.example.myledger.analysis.model.CategoryBreakdown
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.analysis.model.ExpenseSummary
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType

object ExpenseAnalyzer {
    private fun isDaily(row: TransactionEntity): Boolean = row.activityId == null && !row.reimbursable

    fun summarize(rows: List<TransactionEntity>): ExpenseSummary {
        var total = 0L
        var daily = 0L
        var activity = 0L
        var reimbursable = 0L
        rows.filter { it.type == TransactionType.EXPENSE }.forEach {
            total = Math.addExact(total, it.amountMinor)
            if (isDaily(it)) daily = Math.addExact(daily, it.amountMinor)
            if (it.activityId != null) activity = Math.addExact(activity, it.amountMinor)
            if (it.reimbursable) reimbursable = Math.addExact(reimbursable, it.amountMinor)
        }
        // Activity and reimbursable subtotals can overlap. Daily excludes their union once.
        return ExpenseSummary(total, daily, activity, reimbursable)
    }

    fun categoryBreakdown(rows: List<TransactionEntity>, scope: ExpenseScope): List<CategoryBreakdown> =
        rows.filter { it.type == TransactionType.EXPENSE && (scope == ExpenseScope.ALL || isDaily(it)) }
            .groupBy { it.categoryId }
            .map { (category, items) ->
                CategoryBreakdown(category, items.fold(0L) { sum, row -> Math.addExact(sum, row.amountMinor) }, items.size)
            }
            .sortedWith(compareByDescending<CategoryBreakdown> { it.amountMinor }.thenBy { it.categoryId })
}
