package com.example.myledger.analysis

import com.example.myledger.analysis.model.ActivityExpenseSummary
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class MonthSummaryTest {
    private val month = YearMonth.of(2026, 10)
    private fun row(amount: Long, type: TransactionType = TransactionType.EXPENSE, activity: Long? = null,
        reimbursable: Boolean = false, date: LocalDate = month.atDay(3)) = TransactionEntity(
        type = type, amountMinor = amount, categoryId = when (type) {
            TransactionType.EXPENSE -> 1; TransactionType.INCOME -> 13; TransactionType.REIMBURSEMENT -> 16
        }, date = date, activityId = activity, reimbursable = reimbursable)

    @Test fun monthUsesSharedScopesSeparatesReimbursementsAndReportsOnlyActivityExpenses() {
        val rows = listOf(row(1234), row(2000, activity = 1), row(3000, reimbursable = true),
            row(4000, activity = 2, reimbursable = true), row(100000, TransactionType.INCOME, activity = 1),
            row(5000, TransactionType.REIMBURSEMENT, activity = 2))
        val actual = FinancialAnalysis.summarizeMonth(rows, month)
        val period = FinancialAnalysis.summarize(rows, month.atDay(1), month.atEndOfMonth())
        assertEquals(period, actual.period); assertEquals(6, actual.recordCount)
        assertEquals(100000L, actual.period!!.income.ordinaryMinor); assertEquals(5000L, actual.period.income.reimbursementMinor)
        assertEquals(10234L, actual.period.expense.totalMinor); assertEquals(1234L, actual.period.expense.dailyMinor)
        assertEquals(94766L, actual.balanceMinor)
        assertEquals(listOf(ActivityExpenseSummary(2, 4000, 1), ActivityExpenseSummary(1, 2000, 1)), actual.activityExpenses)
    }
    @Test fun localMonthIncludesEndpointsAcrossLeapFebruaryAndYearBoundary() {
        for (period in listOf(YearMonth.of(2024, 2), YearMonth.of(2026, 12), YearMonth.of(2027, 1))) {
            val rows = listOf(row(100, date = period.atDay(1)).copy(createdAt = Long.MAX_VALUE),
                row(200, date = period.atEndOfMonth()).copy(createdAt = 0),
                row(400, date = period.atDay(1).minusDays(1)), row(800, date = period.atEndOfMonth().plusDays(1)))
            val actual = FinancialAnalysis.summarizeMonth(rows, period)
            assertEquals(2, actual.recordCount); assertEquals(300L, actual.period!!.expense.totalMinor)
            assertEquals(period.atDay(1), actual.period.start); assertEquals(period.atEndOfMonth(), actual.period.end)
        }
    }
    @Test fun emptyMonthReturnsZeroBalanceAndNoActivityGroups() {
        val actual = FinancialAnalysis.summarizeMonth(listOf(row(100, date = month.atDay(1).minusDays(1))), month)
        assertEquals(0, actual.recordCount); assertEquals(0L, actual.balanceMinor)
        assertEquals(0L, actual.period!!.income.ordinaryMinor); assertEquals(0L, actual.period.expense.totalMinor)
        assertTrue(actual.activityExpenses.isEmpty())
    }
    @Test fun negativeBalanceIsExactAndCanRecoverThroughReimbursement() {
        assertEquals(-1234L, FinancialAnalysis.summarizeMonth(listOf(row(1234)), month).balanceMinor)
        assertEquals(100L, FinancialAnalysis.summarizeMonth(listOf(row(1234), row(1334, TransactionType.REIMBURSEMENT)), month).balanceMinor)
    }
    @Test fun expenseOffsetAvoidsFalseOverflowWhenIncomingSubtotalsTogetherExceedLong() {
        val actual = FinancialAnalysis.summarizeMonth(listOf(row(Long.MAX_VALUE, TransactionType.INCOME),
            row(10, TransactionType.REIMBURSEMENT), row(Long.MAX_VALUE)), month)
        assertNotNull(actual.period); assertEquals(10L, actual.balanceMinor)
    }
    @Test fun trueBalanceOverflowRetainsValidIndividualSummaries() {
        val actual = FinancialAnalysis.summarizeMonth(listOf(row(Long.MAX_VALUE, TransactionType.INCOME), row(1, TransactionType.REIMBURSEMENT)), month)
        assertNotNull(actual.period); assertNull(actual.balanceMinor)
        assertEquals(Long.MAX_VALUE, actual.period!!.income.ordinaryMinor); assertEquals(1L, actual.period.income.reimbursementMinor)
        assertThrows(ArithmeticException::class.java) { FinancialAnalysis.cashBalanceMinor(actual.period) }
    }
    @Test fun monthlyOverflowRetainsIndividuallyRepresentableActivityGroups() {
        val actual = FinancialAnalysis.summarizeMonth(listOf(row(Long.MAX_VALUE, activity = 1), row(1, activity = 2)), month)
        assertNull(actual.period); assertNull(actual.balanceMinor); assertEquals(2, actual.recordCount)
        assertEquals(listOf(ActivityExpenseSummary(1, Long.MAX_VALUE, 1), ActivityExpenseSummary(2, 1, 1)), actual.activityExpenses)
    }
    @Test fun overflowingActivityStaysVisibleAndEqualAmountsSortById() {
        val actual = FinancialAnalysis.summarizeMonth(listOf(row(200, activity = 3), row(Long.MAX_VALUE, activity = 1),
            row(200, activity = 2), row(1, activity = 1)), month)
        assertEquals(listOf(ActivityExpenseSummary(1, null, 2), ActivityExpenseSummary(2, 200, 1), ActivityExpenseSummary(3, 200, 1)), actual.activityExpenses)
    }
}
