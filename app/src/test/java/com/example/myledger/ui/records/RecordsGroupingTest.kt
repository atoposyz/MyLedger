package com.example.myledger.ui.records

import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecordsGroupingTest {
    private val today = LocalDate.of(2026, 10, 3)
    private fun expense(id: Long, date: LocalDate = today, amount: Long = 1234) =
        TransactionEntity(id = id, type = TransactionType.EXPENSE, amountMinor = amount, categoryId = 1, date = date)
    private fun group(rows: List<TransactionEntity>) = RecordsGrouping.group(rows, DefaultCategories.all, emptyList())

    @Test fun sortsDaysAndRowsDescendingAcrossMonthsAndYears() {
        val previousYear = LocalDate.of(2025, 12, 31)
        val days = group(listOf(expense(2, today.minusMonths(1)), expense(1), expense(3), expense(4, previousYear)))
        assertEquals(listOf(today, today.minusMonths(1), previousYear), days.map { it.date })
        assertEquals(listOf(3L, 1L), days.first().records.map { it.transaction.id })
        assertEquals(2468L, days.first().summary!!.expense.totalMinor)
    }
    @Test fun dailySummarySeparatesOrdinaryIncomeAndReimbursementAndIncludesAllExpenses() {
        val rows = listOf(expense(1, amount = 100), expense(2, amount = 200).copy(activityId = 5),
            expense(3, amount = 300).copy(reimbursable = true),
            expense(4, amount = 400).copy(type = TransactionType.INCOME, categoryId = 12),
            expense(5, amount = 500).copy(type = TransactionType.REIMBURSEMENT, categoryId = 16))
        val summary = group(rows).single().summary!!
        assertEquals(600L, summary.expense.totalMinor); assertEquals(100L, summary.expense.dailyMinor)
        assertEquals(400L, summary.income.ordinaryMinor); assertEquals(500L, summary.income.reimbursementMinor)
    }
    @Test fun resolvesCategoryAndActivityWithoutLosingNotesOrFlags() {
        val original = expense(1).copy(activityId = 5, reimbursable = true, note = "长备注".repeat(100))
        val row = RecordsGrouping.group(listOf(original), DefaultCategories.all,
            listOf(ActivityEntity(5, "会议", ActivityType.WORK))).single().records.single()
        assertEquals("餐饮", row.categoryName); assertEquals("会议", row.activityName)
        assertEquals(original, row.transaction)
    }
    @Test fun overflowingDayKeepsRecordsAndOtherDaysAvailable() {
        val days = group(listOf(expense(1, amount = Long.MAX_VALUE), expense(2, amount = 1),
            expense(3, today.minusDays(1), amount = 10)))
        assertNull(days.first().summary); assertEquals(2, days.first().records.size)
        assertEquals(10L, days.last().summary!!.expense.totalMinor)
        assertTrue(group(emptyList()).isEmpty())
    }
    @Test fun rangeRejectsReversedDatesAndAcceptsSameDay() {
        assertEquals(today, RecordDateRange(today, today).end)
        assertThrows(IllegalArgumentException::class.java) { RecordDateRange(today, today.minusDays(1)) }
    }
}
