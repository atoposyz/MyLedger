package com.example.myledger.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myledger.R
import com.example.myledger.ui.components.EmptyState

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    EmptyState(R.string.settings_empty_title, R.string.settings_empty_message, modifier)
}
