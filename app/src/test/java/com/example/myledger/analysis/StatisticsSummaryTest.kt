package com.example.myledger.analysis

import com.example.myledger.analysis.model.*
import com.example.myledger.data.local.entity.*
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class StatisticsSummaryTest {
    private val month = YearMonth.of(2027, 1)
    private fun row(amount: Long, category: Long = 1, activity: Long? = null, reimbursable: Boolean = false,
        date: LocalDate = month.atDay(3), type: TransactionType = TransactionType.EXPENSE) =
        TransactionEntity(type = type, amountMinor = amount, categoryId = category, activityId = activity,
            reimbursable = reimbursable, date = date)

    @Test fun bothScopesShareHomeRulesAndSwitchTotalCategoriesAndEveryTrendMonth() {
        val current = listOf(row(100, 1), row(300, 2), row(200, 1, activity = 7), row(400, 2, reimbursable = true),
            row(500, 3, activity = 7, reimbursable = true), row(999, type = TransactionType.INCOME), row(888, type = TransactionType.REIMBURSEMENT))
        val rows = current + listOf(row(20, date = month.minusMonths(1).atDay(1)), row(50, activity = 7, date = month.minusMonths(1).atEndOfMonth()))
        val daily = FinancialAnalysis.summarizeStatistics(rows, month, ExpenseScope.DAILY)
        val all = FinancialAnalysis.summarizeStatistics(rows, month, ExpenseScope.ALL)
        val home = FinancialAnalysis.summarizeMonth(rows, month).period!!
        assertEquals(home.expense.dailyMinor, daily.totalMinor); assertEquals(home.expense.totalMinor, all.totalMinor)
        assertEquals(home.dailyCategories, daily.categories); assertEquals(home.allCategories, all.categories)
        assertEquals(400L, daily.totalMinor); assertEquals(1500L, all.totalMinor)
        assertEquals(listOf(2L, 1L), daily.categories.map { it.categoryId })
        assertEquals(listOf(2L, 3L, 1L), all.categories.map { it.categoryId })
        assertEquals(20L, daily.trend[4].amountMinor); assertEquals(70L, all.trend[4].amountMinor)
        assertEquals(400L, daily.trendMaximumMinor); assertEquals(1500L, all.trendMaximumMinor)
    }
    @Test fun sixNaturalMonthsIncludeBothEndpointsAcrossYearAndLeapDayAndFillGapsWithZero() {
        for (target in listOf(month, YearMonth.of(2024, 2))) {
            val start = target.minusMonths(5).atDay(1)
            val end = target.atEndOfMonth()
            val rows = listOf(row(100, date = start).copy(createdAt = Long.MAX_VALUE), row(200, date = end).copy(createdAt = 0),
                row(400, date = start.minusDays(1)), row(800, date = end.plusDays(1)))
            val summary = FinancialAnalysis.summarizeStatistics(rows, target, ExpenseScope.DAILY)
            assertEquals((5 downTo 0).map { target.minusMonths(it.toLong()) }, summary.trend.map { it.month })
            assertEquals(listOf(100L, 0L, 0L, 0L, 0L, 200L), summary.trend.map { it.amountMinor })
            assertEquals(200L, summary.totalMinor)
        }
    }
    @Test fun emptyAndIncomeOnlyMonthsHaveNoExpenseCategoriesAndSixRealZeros() {
        for (scope in ExpenseScope.entries) {
            val summary = FinancialAnalysis.summarizeStatistics(listOf(row(Long.MAX_VALUE, type = TransactionType.INCOME),
                row(Long.MAX_VALUE, type = TransactionType.REIMBURSEMENT)), month, scope)
            assertEquals(0L, summary.totalMinor); assertTrue(summary.categories.isEmpty())
            assertEquals(List(6) { 0L }, summary.trend.map { it.amountMinor }); assertEquals(0L, summary.trendMaximumMinor)
        }
    }
    @Test fun rankingAggregatesCategoriesCountsRecordsAndBreaksTiesByStableId() {
        val summary = FinancialAnalysis.summarizeStatistics(listOf(row(100, 2), row(50, 1), row(50, 1)), month, ExpenseScope.DAILY)
        assertEquals(listOf(CategoryBreakdown(1, 100, 2), CategoryBreakdown(2, 100, 1)), summary.categories)
        assertEquals(200L, summary.totalMinor)
    }
    @Test fun excludedExpenseOverflowAndIncomeOverflowDoNotBreakDailyScope() {
        val rows = listOf(row(1234), row(Long.MAX_VALUE, activity = 1), row(1, reimbursable = true),
            row(Long.MAX_VALUE, type = TransactionType.INCOME), row(1, type = TransactionType.INCOME))
        val daily = FinancialAnalysis.summarizeStatistics(rows, month, ExpenseScope.DAILY)
        assertEquals(1234L, daily.totalMinor); assertEquals(listOf(CategoryBreakdown(1, 1234, 1)), daily.categories)
        assertNull(FinancialAnalysis.summarizeStatistics(rows, month, ExpenseScope.ALL).totalMinor)
    }
    @Test fun currentOverflowSuppressesMisleadingRankingButKeepsOtherMonths() {
        val summary = FinancialAnalysis.summarizeStatistics(listOf(row(Long.MAX_VALUE), row(1, 2),
            row(321, date = month.minusMonths(1).atDay(1))), month, ExpenseScope.ALL)
        assertNull(summary.totalMinor); assertTrue(summary.categories.isEmpty()); assertNull(summary.trend.last().amountMinor)
        assertEquals(321L, summary.trend[4].amountMinor); assertEquals(321L, summary.trendMaximumMinor)
    }
    @Test fun historicalOverflowDoesNotBreakCurrentTotalOrOtherTrendMonths() {
        val previous = month.minusMonths(1).atDay(1)
        val summary = FinancialAnalysis.summarizeStatistics(listOf(row(100), row(Long.MAX_VALUE, date = previous), row(1, date = previous)), month, ExpenseScope.DAILY)
        assertEquals(100L, summary.totalMinor); assertEquals(100L, summary.categories.single().amountMinor)
        assertNull(summary.trend[4].amountMinor); assertEquals(100L, summary.trendMaximumMinor)
    }
    @Test fun maximumLongAmountStaysExactInTotalCategoryAndTrend() {
        val summary = FinancialAnalysis.summarizeStatistics(listOf(row(Long.MAX_VALUE)), month, ExpenseScope.ALL)
        assertEquals(Long.MAX_VALUE, summary.totalMinor); assertEquals(Long.MAX_VALUE, summary.categories.single().amountMinor)
        assertEquals(Long.MAX_VALUE, summary.trend.last().amountMinor); assertEquals(Long.MAX_VALUE, summary.trendMaximumMinor)
    }
}
