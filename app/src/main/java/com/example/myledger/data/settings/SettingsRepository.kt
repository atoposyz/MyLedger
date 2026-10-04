package com.example.myledger.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.ledgerSettings by preferencesDataStore(name = "ledger_settings")

data class BackupPreferences(val automatic: Boolean = false, val lastSuccessMillis: Long? = null, val lastAttemptFailed: Boolean = false)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val automaticKey = booleanPreferencesKey("backup_automatic")
    private val successKey = longPreferencesKey("backup_last_success")
    private val failedKey = booleanPreferencesKey("backup_last_failed")
    val backupPreferences = store.data.map { BackupPreferences(it[automaticKey] ?: false, it[successKey], it[failedKey] ?: false) }
    suspend fun currentBackupPreferences() = backupPreferences.first()
    suspend fun setAutomaticBackup(enabled: Boolean) { store.edit { it[automaticKey] = enabled } }
    suspend fun recordBackupResult(successMillis: Long?) { store.edit {
        it[failedKey] = successMillis == null
        if (successMillis != null) it[successKey] = successMillis
    } }
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
