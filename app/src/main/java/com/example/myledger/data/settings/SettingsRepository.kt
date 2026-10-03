package com.example.myledger.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ledgerSettings by preferencesDataStore(name = "ledger_settings")

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme_mode")
    val settings = store.data.map { preferences ->
        AppSettings(ThemeMode.entries.firstOrNull { it.name == preferences[themeKey] } ?: ThemeMode.SYSTEM)
    }
    suspend fun current(): AppSettings = settings.first()
    suspend fun update(settings: AppSettings) { store.edit { it[themeKey] = settings.themeMode.name } }
    suspend fun setTheme(themeMode: ThemeMode) = update(AppSettings(themeMode))
    companion object {
        fun create(context: Context) = SettingsRepository(context.applicationContext.ledgerSettings)
    }
}
