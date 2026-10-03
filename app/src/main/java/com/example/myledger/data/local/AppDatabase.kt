package com.example.myledger.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.myledger.data.local.dao.ActivityDao
import com.example.myledger.data.local.dao.CategoryDao
import com.example.myledger.data.local.dao.TransactionDao
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionEntity

@Database(
    entities = [TransactionEntity::class, CategoryEntity::class, ActivityEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(LedgerConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun activityDao(): ActivityDao

    companion object {
        const val NAME = "myledger.db"

        // onCreate runs within Room's creation transaction, before any DAO can read.
        val seedCategories: Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                DefaultCategories.all.forEach { category ->
                    db.execSQL(
                        "INSERT INTO categories (id, name, type, sortOrder) VALUES (?, ?, ?, ?)",
                        arrayOf<Any>(category.id, category.name, category.type.name, category.sortOrder),
                    )
                }
            }
        }

        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, NAME)
                .addCallback(seedCategories)
                .build()
    }
}
