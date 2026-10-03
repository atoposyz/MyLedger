package com.example.myledger.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object LedgerDates {
    // Material DatePicker represents a selected calendar day at UTC midnight.
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
    fun formatDay(date: LocalDate, locale: Locale): String = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", locale))
}
