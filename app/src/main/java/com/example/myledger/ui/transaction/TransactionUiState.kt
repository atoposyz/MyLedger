package com.example.myledger.ui.transaction

import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionType
import java.time.LocalDate

data class TransactionForm(
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val categoryId: Long? = null,
    val activityId: Long? = null,
    val reimbursable: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val note: String = "",
)

data class SavedTransaction(
    val id: Long, val type: TransactionType, val amountMinor: Long,
    val categoryName: String, val date: LocalDate,
)

data class TransactionUiState(
    val form: TransactionForm,
    val allCategories: List<CategoryEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val amountError: Boolean = false,
    val saveFailed: Boolean = false,
    val saved: SavedTransaction? = null,
    val editingId: Long? = null,
    val missingRecord: Boolean = false,
    val isDeleting: Boolean = false,
    val deleteFailed: Boolean = false,
    val deleted: Boolean = false,
) {
    val categories: List<CategoryEntity> get() = allCategories.filter { it.type == form.type }
    val selectedCategory: CategoryEntity? get() = categories.firstOrNull { it.id == form.categoryId } ?: categories.firstOrNull()
    val selectedActivity: ActivityEntity? get() = activities.firstOrNull { it.id == form.activityId }
}
