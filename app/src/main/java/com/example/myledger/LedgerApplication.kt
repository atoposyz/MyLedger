package com.example.myledger

import android.app.Application
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.SettingsRepository
import com.example.myledger.backup.BackupManager
import com.example.myledger.backup.LocalBackupScheduler

class LedgerApplication : Application() {
    val financialTools by lazy { com.example.myledger.assistant.FinancialTools(repository) }
    val assistantService by lazy { com.example.myledger.assistant.AssistantService(integrationSettings, financialTools) }
    val integrationSettings by lazy { com.example.myledger.data.settings.IntegrationSettingsRepository.create(this) }
    val remoteBackups by lazy { com.example.myledger.backup.RemoteBackupRepository(integrationSettings, backupManager) }
    val backupScheduler: LocalBackupScheduler by lazy { LocalBackupScheduler(this) }
    val backupManager: BackupManager by lazy { BackupManager(this, repository, settingsRepository) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(AppDatabase.create(this))
    }
}
