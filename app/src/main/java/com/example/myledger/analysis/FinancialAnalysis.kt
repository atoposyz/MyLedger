package com.example.myledger.analysis

import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.analysis.model.ActivityExpenseSummary
import com.example.myledger.analysis.model.MonthSummary
import com.example.myledger.analysis.model.PeriodSummary
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FinancialAnalysis(private val repository: LedgerRepository) {
    suspend fun getPeriodSummary(start: LocalDate, end: LocalDate): PeriodSummary =
        summarize(repository.getTransactions(start, end), start, end)

    fun observePeriodSummary(start: LocalDate, end: LocalDate): Flow<PeriodSummary> =
        repository.observeTransactions(start, end).map { summarize(it, start, end) }

    companion object {
        fun summarizeMonth(rows: List<TransactionEntity>, month: YearMonth): MonthSummary {
            val start = month.atDay(1)
            val end = month.atEndOfMonth()
            val period = rows.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            val summary = try { summarize(period, start, end) } catch (_: ArithmeticException) { null }
            val balance = summary?.let { try { cashBalanceMinor(it) } catch (_: ArithmeticException) { null } }
            val activityExpenses = period.filter { it.type == TransactionType.EXPENSE && it.activityId != null }
                .groupBy { requireNotNull(it.activityId) }.map { (activityId, items) ->
                    val amount = try { ExpenseAnalyzer.summarize(items).totalMinor } catch (_: ArithmeticException) { null }
                    ActivityExpenseSummary(activityId, amount, items.size)
                }.sortedWith(compareBy<ActivityExpenseSummary> { it.amountMinor != null }
                    .thenByDescending { it.amountMinor }.thenBy { it.activityId })
            return MonthSummary(month, period.size, summary, balance, activityExpenses)
        }

        fun cashBalanceMinor(summary: PeriodSummary): Long = Math.addExact(
            Math.subtractExact(summary.income.ordinaryMinor, summary.expense.totalMinor),
            summary.income.reimbursementMinor,
        )

        fun summarize(rows: List<TransactionEntity>, start: LocalDate, end: LocalDate): PeriodSummary {
            require(!end.isBefore(start)) { "结束日期不能早于开始日期" }
            val period = rows.filter { !it.date.isBefore(start) && !it.date.isAfter(end) }
            return PeriodSummary(
                start, end, ExpenseAnalyzer.summarize(period), IncomeAnalyzer.summarize(period),
                ExpenseAnalyzer.categoryBreakdown(period, ExpenseScope.DAILY),
                ExpenseAnalyzer.categoryBreakdown(period, ExpenseScope.ALL),
            )
        }
    }
}
