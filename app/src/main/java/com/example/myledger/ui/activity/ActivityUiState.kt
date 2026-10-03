package com.example.myledger.ui.activity

import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import java.time.LocalDate

data class ActivityListUiState(
    val activities: List<ActivityEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
)

data class ActivityForm(
    val name: String = "",
    val type: ActivityType = ActivityType.PERSONAL,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val note: String = "",
) {
    val invalidDates: Boolean get() = startDate != null && endDate != null && endDate.isBefore(startDate)
}

data class ActivityEditorUiState(
    val form: ActivityForm = ActivityForm(),
    val editingId: Long? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    val missing: Boolean = false,
    val nameError: Boolean = false,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    val savedId: Long? = null,
    val checkingDelete: Boolean = false,
    val deleteUsage: Long? = null,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val deleted: Boolean = false,
) {
    val busy: Boolean get() = isLoading || isSaving || checkingDelete || isDeleting || savedId != null || deleted
}
