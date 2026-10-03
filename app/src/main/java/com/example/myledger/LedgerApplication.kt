package com.example.myledger

import android.app.Application
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.repository.LedgerRepository
import com.example.myledger.data.settings.SettingsRepository

class LedgerApplication : Application() {
    val settingsRepository: SettingsRepository by lazy { SettingsRepository.create(this) }
    val repository: LedgerRepository by lazy {
        LedgerRepository(AppDatabase.create(this))
    }
}
