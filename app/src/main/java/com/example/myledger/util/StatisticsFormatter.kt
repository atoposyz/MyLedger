package com.example.myledger.util

import java.math.BigDecimal
import java.math.RoundingMode

// Ratios are display values only. Financial amounts stay Long throughout analysis and storage.
object StatisticsFormatter {
    fun share(amountMinor: Long, totalMinor: Long): Float = if (totalMinor <= 0) 0f else
        BigDecimal.valueOf(amountMinor).divide(BigDecimal.valueOf(totalMinor), 8, RoundingMode.HALF_UP)
            .toFloat().coerceIn(0f, 1f)

    fun percentage(amountMinor: Long, totalMinor: Long): String {
        if (totalMinor <= 0 || amountMinor == 0L) return "0.0%"
        val percentage = BigDecimal.valueOf(amountMinor).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(totalMinor), 1, RoundingMode.HALF_UP)
        return if (percentage.signum() == 0) "<0.1%" else "${percentage.toPlainString()}%"
    }
}
