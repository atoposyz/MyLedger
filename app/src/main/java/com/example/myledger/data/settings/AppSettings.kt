package com.example.myledger.data.settings

enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class AppSettings(val themeMode: ThemeMode = ThemeMode.SYSTEM) {
    val currency: String get() = "CNY"
}
