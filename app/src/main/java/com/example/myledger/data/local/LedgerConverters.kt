package com.example.myledger.data.local

import androidx.room.TypeConverter
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.TransactionType
import java.time.LocalDate

class LedgerConverters {
    @TypeConverter fun dateToEpochDay(value: LocalDate?): Long? = value?.toEpochDay()
    @TypeConverter fun epochDayToDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @TypeConverter fun transactionTypeToString(value: TransactionType): String = value.name
    @TypeConverter fun stringToTransactionType(value: String): TransactionType = TransactionType.valueOf(value)
    @TypeConverter fun activityTypeToString(value: ActivityType): String = value.name
    @TypeConverter fun stringToActivityType(value: String): ActivityType = ActivityType.valueOf(value)
}
