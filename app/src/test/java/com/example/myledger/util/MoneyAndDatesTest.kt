package com.example.myledger.util

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.Test

class MoneyAndDatesTest {
    @Test fun signedCurrencyFormattingSupportsNegativeZeroAndLongExtremes() {
        for ((input, expected) in listOf(0L to "¥0.00", 1L to "¥0.01", -1L to "−¥0.01", -1234L to "−¥12.34",
            Long.MAX_VALUE to "¥92233720368547758.07", Long.MIN_VALUE to "−¥92233720368547758.08")) {
            assertEquals(expected, MoneyInput.formatCurrencyMinor(input))
        }
    }
    @Test fun monthFormatterIncludesYearAndNaturalMonth() {
        assertEquals("2026年10月", LedgerDates.formatMonth(YearMonth.of(2026, 10), Locale.SIMPLIFIED_CHINESE))
        assertEquals("2027年1月", LedgerDates.formatMonth(YearMonth.of(2027, 1), Locale.SIMPLIFIED_CHINESE))
    }
    @Test fun parsesExactMinorUnitsAndCommonDecimalInput() {
        for ((input, expected) in listOf("12.34" to 1234L, "12" to 1200L, "12." to 1200L,
            ".5" to 50L, "0.01" to 1L, "00012.30" to 1230L, " 12.34 " to 1234L)) {
            assertEquals(input, expected, MoneyInput.parseMinor(input))
        }
    }
    @Test fun rejectsZeroNegativeExcessPrecisionAndMalformedInput() {
        for (input in listOf("", " ", "0", "0.00", "-1", "12.345", "1,23", "1e3", "NaN", "abc", ".", "1..2")) {
            assertNull(input, MoneyInput.parseMinor(input))
        }
    }
    @Test fun maximumLongIsExactAndOverflowIsRejected() {
        assertEquals(Long.MAX_VALUE, MoneyInput.parseMinor("92233720368547758.07"))
        assertNull(MoneyInput.parseMinor("92233720368547758.08"))
        assertNull(MoneyInput.parseMinor("99999999999999999999999"))
        assertEquals("92233720368547758.07", MoneyInput.formatMinor(Long.MAX_VALUE))
        assertEquals("0.01", MoneyInput.formatMinor(1))
        assertEquals("12.30", MoneyInput.formatMinor(1230))
    }
    @Test fun datePickerDayIsStableAcrossTimeZonesAndDaylightSaving() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("Asia/Shanghai", "America/Los_Angeles", "Pacific/Kiritimati")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                for (date in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 10, 3), LocalDate.of(2024, 2, 29))) {
                    assertEquals(date, LedgerDates.fromPickerMillis(LedgerDates.toPickerMillis(date)))
                }
            }
        } finally { TimeZone.setDefault(original) }
    }
}
