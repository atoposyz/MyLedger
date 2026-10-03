package com.example.myledger.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.myledger.data.local.entity.ActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Query("SELECT * FROM activities ORDER BY id")
    suspend fun getAll(): List<ActivityEntity>
    @Query("DELETE FROM activities")
    suspend fun deleteAll()
    @Insert suspend fun insertAll(activities: List<ActivityEntity>)
    @Query("SELECT * FROM activities ORDER BY id DESC")
    fun observeAll(): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities WHERE id = :id")
    suspend fun getById(id: Long): ActivityEntity?

    @Insert suspend fun insert(activity: ActivityEntity): Long
    @Update suspend fun update(activity: ActivityEntity): Int

    // Referenced activities are protected by RESTRICT, never cascade ledger deletion.
    @Query("DELETE FROM activities WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
