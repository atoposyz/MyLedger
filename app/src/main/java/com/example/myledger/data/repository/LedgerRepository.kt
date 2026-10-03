package com.example.myledger.data.repository

import androidx.room.withTransaction
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionEntity
import com.example.myledger.data.local.entity.TransactionType
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class LedgerRepository(
    private val database: AppDatabase,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val transactions = database.transactionDao()
    private val categories = database.categoryDao()
    private val activities = database.activityDao()

    // Force the initial open (including category seeding) on Room's coroutine executor.
    suspend fun initialize() { categories.getAll() }

    suspend fun snapshot(): LedgerSnapshot = database.withTransaction {
        LedgerSnapshot(transactions.getAll(), activities.getAll(), categories.getAll())
    }

    suspend fun replaceLedger(snapshot: LedgerSnapshot, beforeCommit: suspend () -> Unit = {}) = database.withTransaction {
        snapshot.validate()
        transactions.deleteAll(); activities.deleteAll()
        activities.insertAll(snapshot.activities)
        transactions.insertAll(snapshot.transactions)
        beforeCommit()
    }

    fun observeTransactions(): Flow<List<TransactionEntity>> = transactions.observeAll()
    fun observeTransactions(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>> {
        require(!end.isBefore(start)) { "结束日期不能早于开始日期" }
        return transactions.observeBetween(start, end)
    }

    suspend fun getTransactions(start: LocalDate, end: LocalDate): List<TransactionEntity> {
        require(!end.isBefore(start)) { "结束日期不能早于开始日期" }
        return transactions.getBetween(start, end)
    }

    suspend fun getTransaction(id: Long): TransactionEntity? = transactions.getById(id)
    fun observeCategories(type: TransactionType): Flow<List<CategoryEntity>> = categories.observeByType(type)
    fun observeActivities(): Flow<List<ActivityEntity>> = activities.observeAll()
    suspend fun getActivity(id: Long): ActivityEntity? = activities.getById(id)

    suspend fun addTransaction(transaction: TransactionEntity): Long = database.withTransaction {
        require(transaction.id == 0L) { "新增账目不能指定 ID" }
        validateTransaction(transaction)
        val now = clock.millis()
        transactions.insert(transaction.copy(createdAt = now, updatedAt = now))
    }

    // Each row remains an independent transaction entity; no batch entity is stored.
    suspend fun addTransactions(items: List<TransactionEntity>): List<Long> = database.withTransaction {
        items.forEach {
            require(it.id == 0L) { "新增账目不能指定 ID" }
            validateTransaction(it)
        }
        val now = clock.millis()
        transactions.insertAll(items.map { it.copy(createdAt = now, updatedAt = now) })
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = database.withTransaction {
        val original = requireNotNull(transactions.getById(transaction.id)) { "账目不存在" }
        validateTransaction(transaction)
        transactions.update(transaction.copy(createdAt = original.createdAt, updatedAt = clock.millis()))
        Unit
    }

    suspend fun deleteTransaction(id: Long): Boolean = transactions.deleteById(id) != 0

    suspend fun addActivity(activity: ActivityEntity): Long {
        require(activity.id == 0L) { "新增活动不能指定 ID" }
        validateActivity(activity)
        return activities.insert(activity.copy(name = activity.name.trim()))
    }

    suspend fun updateActivity(activity: ActivityEntity) {
        validateActivity(activity)
        require(activities.update(activity.copy(name = activity.name.trim())) == 1) { "活动不存在" }
    }

    suspend fun deleteActivity(id: Long): Boolean = activities.deleteById(id) != 0

    suspend fun countActivityTransactions(id: Long): Long = transactions.countByActivity(id)

    // Recheck inside the same transaction: a new linked record cannot race confirmation.
    suspend fun deleteActivityIfUnused(id: Long): ActivityDeleteResult = database.withTransaction {
        if (activities.getById(id) == null) return@withTransaction ActivityDeleteResult.Missing
        val count = transactions.countByActivity(id)
        if (count > 0) ActivityDeleteResult.InUse(count)
        else {
            activities.deleteById(id)
            ActivityDeleteResult.Deleted
        }
    }

    private suspend fun validateTransaction(transaction: TransactionEntity) {
        require(transaction.amountMinor > 0) { "金额必须大于零" }
        val category = requireNotNull(categories.getById(transaction.categoryId)) { "分类不存在" }
        require(category.type == transaction.type) { "分类与账目类型不匹配" }
        transaction.activityId?.let { requireNotNull(activities.getById(it)) { "活动不存在" } }
    }

    private fun validateActivity(activity: ActivityEntity) {
        require(activity.name.isNotBlank()) { "活动名称不能为空" }
        if (activity.startDate != null && activity.endDate != null) {
            require(!activity.endDate.isBefore(activity.startDate)) { "结束日期不能早于开始日期" }
        }
    }
}

sealed interface ActivityDeleteResult {
    data object Deleted : ActivityDeleteResult
    data object Missing : ActivityDeleteResult
    data class InUse(val count: Long) : ActivityDeleteResult
}
