package com.example.myledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.myledger.data.local.entity.TransactionEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    // Both endpoints are inclusive; dates bind as numeric epoch days.
    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert suspend fun insert(transaction: TransactionEntity): Long
    // Room wraps the entire list insert in one transaction, including rollback on failure.
    @Insert suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>
    @Update suspend fun update(transaction: TransactionEntity): Int

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
