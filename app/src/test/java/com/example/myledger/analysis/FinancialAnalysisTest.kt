package com.example.myledger.analysis

import com.example.myledger.analysis.model.CategoryBreakdown
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class FinancialAnalysisTest {
    private val date = LocalDate.of(2026, 10, 3)
    private fun expense(amount: Long, activity: Long? = null, reimbursable: Boolean = false, category: Long = 1) =
        TransactionEntity(type = TransactionType.EXPENSE, amountMinor = amount, categoryId = category,
            date = date, activityId = activity, reimbursable = reimbursable)

    @Test fun ordinaryIncomeExcludesReimbursementAndExpenses() {
        val summary = IncomeAnalyzer.summarize(listOf(
            expense(100), expense(200).copy(type = TransactionType.INCOME, categoryId = 12),
            expense(300).copy(type = TransactionType.REIMBURSEMENT, categoryId = 16),
        ))
        assertEquals(200L, summary.ordinaryMinor)
        assertEquals(300L, summary.reimbursementMinor)
    }

    @Test fun activityExpenseIsExcludedFromDaily() {
        val summary = ExpenseAnalyzer.summarize(listOf(expense(100, activity = 1)))
        assertEquals(100L, summary.totalMinor)
        assertEquals(100L, summary.activityMinor)
        assertEquals(0L, summary.dailyMinor)
    }

    @Test fun reimbursableExpenseIsExcludedFromDailyWithoutRequiringActivity() {
        val summary = ExpenseAnalyzer.summarize(listOf(expense(200, reimbursable = true)))
        assertEquals(200L, summary.totalMinor)
        assertEquals(200L, summary.reimbursableMinor)
        assertEquals(0L, summary.dailyMinor)
    }

    @Test fun ordinaryExpenseIsIncludedInDaily() {
        val summary = ExpenseAnalyzer.summarize(listOf(expense(1234)))
        assertEquals(1234L, summary.totalMinor)
        assertEquals(1234L, summary.dailyMinor)
    }

    @Test fun allExpensesIncludeDailyActivityAndReimbursableAndIgnoreIncome() {
        val summary = ExpenseAnalyzer.summarize(listOf(
            expense(100), expense(200, activity = 1), expense(300, reimbursable = true),
            expense(999).copy(type = TransactionType.INCOME),
            expense(888).copy(type = TransactionType.REIMBURSEMENT),
        ))
        assertEquals(600L, summary.totalMinor)
        assertEquals(100L, summary.dailyMinor)
    }

    @Test fun expenseThatHasActivityAndIsReimbursableIsExcludedOnlyOnce() {
        val summary = ExpenseAnalyzer.summarize(listOf(expense(100), expense(500, activity = 1, reimbursable = true)))
        assertEquals(600L, summary.totalMinor)
        assertEquals(100L, summary.dailyMinor)
        assertEquals(500L, summary.activityMinor)
        assertEquals(500L, summary.reimbursableMinor)
    }

    @Test fun categoryBreakdownUsesSameScopeAndHasDeterministicRanking() {
        val rows = listOf(expense(100), expense(200), expense(300, category = 2),
            expense(400, activity = 1, category = 2), expense(500, reimbursable = true, category = 3))
        assertEquals(listOf(CategoryBreakdown(1, 300, 2), CategoryBreakdown(2, 300, 1)),
            ExpenseAnalyzer.categoryBreakdown(rows, ExpenseScope.DAILY))
        assertEquals(listOf(CategoryBreakdown(2, 700, 2), CategoryBreakdown(3, 500, 1), CategoryBreakdown(1, 300, 2)),
            ExpenseAnalyzer.categoryBreakdown(rows, ExpenseScope.ALL))
    }

    @Test fun periodUsesInclusiveBusinessDatesRatherThanSaveTimestamp() {
        val start = LocalDate.of(2026, 9, 30)
        val end = LocalDate.of(2026, 10, 1)
        val rows = listOf(expense(100).copy(date = start, createdAt = 0), expense(200).copy(date = end),
            expense(300).copy(date = start.minusDays(1)), expense(400).copy(date = end.plusDays(1)))
        val summary = FinancialAnalysis.summarize(rows, start, end)
        assertEquals(300L, summary.expense.totalMinor)
        assertEquals(summary.expense.dailyMinor, summary.dailyCategories.sumOf { it.amountMinor })
        assertEquals(summary.expense.totalMinor, summary.allCategories.sumOf { it.amountMinor })
    }

    @Test fun emptyPeriodReturnsZerosAndNoCategories() {
        val summary = FinancialAnalysis.summarize(emptyList(), date, date)
        assertEquals(0L, summary.expense.totalMinor)
        assertEquals(0L, summary.income.ordinaryMinor)
        assertEquals(0L, summary.income.reimbursementMinor)
        assertTrue(summary.allCategories.isEmpty())
        assertTrue(summary.dailyCategories.isEmpty())
    }

    @Test fun largeAmountsRemainExact() {
        val summary = ExpenseAnalyzer.summarize(listOf(expense(9_007_199_254_740_993L), expense(1234)))
        assertEquals(9_007_199_254_742_227L, summary.totalMinor)
    }

    @Test fun overflowFailsExplicitlyInsteadOfWrappingIntoNegativeAmount() {
        assertThrows(ArithmeticException::class.java) { ExpenseAnalyzer.summarize(listOf(expense(Long.MAX_VALUE), expense(1))) }
        assertThrows(ArithmeticException::class.java) { IncomeAnalyzer.summarize(listOf(
            expense(Long.MAX_VALUE).copy(type = TransactionType.INCOME), expense(1).copy(type = TransactionType.INCOME))) }
    }

    @Test fun invalidPeriodIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { FinancialAnalysis.summarize(emptyList(), date, date.minusDays(1)) }
    }
}
