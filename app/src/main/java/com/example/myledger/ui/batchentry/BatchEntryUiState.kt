package com.example.myledger.ui.batchentry

import com.example.myledger.data.local.entity.ActivityEntity
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.data.local.entity.CategoryEntity
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.util.MoneyInput
import java.time.LocalDate

data class BatchDefaults(
    val date: LocalDate = LocalDate.now(),
    val activityId: Long? = null,
    val reimbursable: Boolean = false,
)

// This is a form draft, never a database batch entity.
data class TransactionDraft(
    val id: Long,
    val type: TransactionType = TransactionType.EXPENSE,
    val amount: String = "",
    val categoryId: Long? = null,
    val date: LocalDate,
    val activityId: Long? = null,
    val reimbursable: Boolean = false,
    val note: String = "",
)

data class SavedBatch(val count: Int, val totalMinor: Long)

data class BatchEntryUiState(
    val defaults: BatchDefaults = BatchDefaults(),
    val drafts: List<TransactionDraft> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val activities: List<ActivityEntity> = emptyList(),
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val invalidAmountIds: Set<Long> = emptySet(),
    val saveFailed: Boolean = false,
    val saved: SavedBatch? = null,
) {
    val defaultIsWork: Boolean get() = activities.any { it.id == defaults.activityId && it.type == ActivityType.WORK }
    fun selectedCategory(draft: TransactionDraft): CategoryEntity? =
        categories.firstOrNull { it.id == draft.categoryId && it.type == draft.type }
            ?: categories.firstOrNull { it.type == draft.type }

    // Gross input total, not income minus expense. Invalid / empty drafts are not counted.
    // A null result means the valid amounts together exceed Long, so saving is blocked.
    val totalMinor: Long? get() = try {
        drafts.fold(0L) { total, draft -> Math.addExact(total, MoneyInput.parseMinor(draft.amount) ?: 0L) }
    } catch (_: ArithmeticException) { null }
}
