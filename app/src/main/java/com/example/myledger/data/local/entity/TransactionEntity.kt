package com.example.myledger.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"], childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ActivityEntity::class,
            parentColumns = ["id"], childColumns = ["activityId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("activityId"), Index("date")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: Long,
    val date: LocalDate,
    val activityId: Long? = null,
    val reimbursable: Boolean = false,
    val note: String? = null,
    // Epoch milliseconds for auditing, independent of the selected business date.
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
)
