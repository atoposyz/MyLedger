package com.example.myledger.ui.batchentry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myledger.R
import com.example.myledger.ui.components.EmptyState

@Composable
fun BatchDailyEntryScreen(modifier: Modifier = Modifier) {
    EmptyState(R.string.batch_entry_empty_title, R.string.batch_entry_empty_message, modifier)
}
