package com.example.myledger.util

object MoneyInput {
    private val decimal = Regex("(?:[0-9]+(?:\\.[0-9]{0,2})?|\\.[0-9]{1,2})")

    fun parseMinor(text: String): Long? {
        val input = text.trim()
        if (!decimal.matches(input)) return null
        val parts = input.split('.', limit = 2)
        val whole = parts[0].trimStart('0').ifEmpty { "0" }.toLongOrNull() ?: return null
        val fraction = parts.getOrElse(1) { "" }.padEnd(2, '0').toLong()
        return try {
            Math.addExact(Math.multiplyExact(whole, 100), fraction).takeIf { it > 0 }
        } catch (_: ArithmeticException) { null }
    }

    fun formatMinor(amountMinor: Long): String {
        require(amountMinor >= 0)
        return "${amountMinor / 100}.${(amountMinor % 100).toString().padStart(2, '0')}"
    }
}
