package com.example.myledger.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE type = :type ORDER BY sortOrder, id")
    fun observeByType(type: TransactionType): Flow<List<CategoryEntity>>
}
