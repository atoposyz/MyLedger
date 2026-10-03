package com.example.myledger.ui.records

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myledger.R
import com.example.myledger.ui.components.EmptyState

@Composable
fun RecordsScreen(modifier: Modifier = Modifier) {
    EmptyState(R.string.records_empty_title, R.string.records_empty_message, modifier)
}
