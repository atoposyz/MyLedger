package com.example.myledger.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.myledger.R
import com.example.myledger.ui.components.EmptyState

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    EmptyState(R.string.home_empty_title, R.string.home_empty_message, modifier)
}
