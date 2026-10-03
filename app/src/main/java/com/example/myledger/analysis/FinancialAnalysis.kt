package com.example.myledger.analysis

import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.analysis.model.PeriodSummary
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.repository.LedgerRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FinancialAnalysis(private val repository: LedgerRepository) {
    suspend fun getPeriodSummary(start: LocalDate, end: LocalDate): PeriodSummary =
        summarize(repository.getTransactions(start, end), start, end)

    fun observePeriodSummary(start: LocalDate, end: LocalDate): Flow<PeriodSummary> =
        repository.observeTransactions(start, end).map { summarize(it, start, end) }

    companion object {
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
