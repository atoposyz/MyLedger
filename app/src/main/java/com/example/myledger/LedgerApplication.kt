package com.example.myledger

import android.app.Application
import com.example.myledger.data.local.AppDatabase
import com.example.myledger.data.repository.LedgerRepository

class LedgerApplication : Application() {
    val repository: LedgerRepository by lazy {
        LedgerRepository(AppDatabase.create(this))
    }
}
