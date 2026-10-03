package com.example.myledger.ui.records

import com.example.myledger.analysis.FinancialAnalysis
import com.example.myledger.analysis.model.PeriodSummary
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionEntity
import java.time.LocalDate

data class RecordDateRange(val start: LocalDate, val end: LocalDate) {
    init { require(!end.isBefore(start)) }
}

data class RecordItem(val transaction: TransactionEntity, val categoryName: String?, val activityName: String?)
data class RecordDay(val date: LocalDate, val records: List<RecordItem>, val summary: PeriodSummary?)
data class RecordsUiState(
    val range: RecordDateRange? = null,
    val days: List<RecordDay> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

object RecordsGrouping {
    fun group(rows: List<TransactionEntity>, categories: List<CategoryEntity>, activities: List<ActivityEntity>): List<RecordDay> {
        val categoryNames = categories.associate { it.id to it.name }
        val activityNames = activities.associate { it.id to it.name }
        return rows.sortedWith(compareByDescending<TransactionEntity> { it.date }.thenByDescending { it.id })
            .groupBy { it.date }.map { (date, items) ->
                // A single oversized day's totals must not hide the actual editable records.
                val summary = try { FinancialAnalysis.summarize(items, date, date) }
                    catch (_: ArithmeticException) { null }
                RecordDay(date, items.map { RecordItem(it, categoryNames[it.categoryId], activityNames[it.activityId]) }, summary)
            }
    }
}
