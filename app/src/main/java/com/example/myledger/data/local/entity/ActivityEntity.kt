package com.example.myledger.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: ActivityType,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val note: String? = null,
)
