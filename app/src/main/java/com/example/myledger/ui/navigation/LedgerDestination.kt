package com.example.myledger.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.example.myledger.R

internal enum class LedgerDestination(
    val route: String,
    @param:StringRes @get:StringRes val title: Int,
    @param:DrawableRes @get:DrawableRes val icon: Int? = null
) {
    HOME("home", R.string.home, R.drawable.ic_home),
    RECORDS("records", R.string.records, R.drawable.ic_records),
    STATISTICS("statistics", R.string.statistics, R.drawable.ic_statistics),
    SETTINGS("settings", R.string.settings, R.drawable.ic_settings),
    SINGLE_ENTRY("single_entry", R.string.single_entry),
    BATCH_ENTRY("batch_entry", R.string.batch_entry),
    EDIT_TRANSACTION("edit_transaction/{transactionId}", R.string.transaction_edit),
    ACTIVITIES("activities", R.string.activities),
    CREATE_ACTIVITY("create_activity", R.string.activity_create),
    EDIT_ACTIVITY("edit_activity/{activityId}", R.string.activity_edit),
    BACKUP("backup", R.string.settings_backup)
}

internal val mainDestinations = listOf(
    LedgerDestination.HOME,
    LedgerDestination.RECORDS,
    LedgerDestination.STATISTICS,
    LedgerDestination.SETTINGS
)
