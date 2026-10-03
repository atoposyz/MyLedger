package com.example.myledger.data.repository

import com.example.myledger.data.local.DefaultCategories
import com.example.myledger.data.local.entity.*

data class LedgerSnapshot(val transactions: List<TransactionEntity>, val activities: List<ActivityEntity>, val categories: List<CategoryEntity>) {
    fun validate() {
        require(categories.sortedBy { it.id } == DefaultCategories.all.sortedBy { it.id }) { "备份分类不受支持" }
        require(transactions.size <= 100_000 && activities.size <= 100_000) { "备份记录过多" }
        require(transactions.all { it.id > 0 && it.id < Long.MAX_VALUE } && transactions.map { it.id }.distinct().size == transactions.size) { "账目 ID 无效" }
        require(activities.all { it.id > 0 && it.id < Long.MAX_VALUE } && activities.map { it.id }.distinct().size == activities.size) { "活动 ID 无效" }
        val activityIds = activities.map { it.id }.toSet()
        val categoryById = categories.associateBy { it.id }
        activities.forEach { require(it.name.isNotBlank() && (it.startDate == null || it.endDate == null || !it.endDate.isBefore(it.startDate))) { "活动信息无效" } }
        transactions.forEach { row ->
            require(row.amountMinor > 0 && categoryById[row.categoryId]?.type == row.type && (row.activityId == null || row.activityId in activityIds)) { "账目或关联无效" }
        }
    }
}
