package com.example.myledger.ui.transaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myledger.R
import com.example.myledger.ui.components.EmptyState

@Composable
fun TransactionScreen(modifier: Modifier = Modifier) {
    EmptyState(R.string.single_entry_empty_title, R.string.single_entry_empty_message, modifier)
}
