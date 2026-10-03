package com.example.myledger.ui.statistics

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myledger.R
import com.example.myledger.ui.components.EmptyState

@Composable
fun StatisticsScreen(modifier: Modifier = Modifier) {
    EmptyState(R.string.statistics_empty_title, R.string.statistics_empty_message, modifier)
}
