package com.example.myledger.ui.home

import com.example.myledger.analysis.model.MonthSummary
import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.ui.records.RecordItem
import java.time.YearMonth

data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val summary: MonthSummary? = null,
    val activityById: Map<Long, ActivityEntity> = emptyMap(),
    val recentRecords: List<RecordItem> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)
