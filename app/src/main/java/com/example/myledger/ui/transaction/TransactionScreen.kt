package com.example.myledger.ui.transaction

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.ui.components.Choice
import com.example.myledger.ui.components.EntryDateDialog
import com.example.myledger.ui.components.SelectionDialog
import java.time.LocalDate

@StringRes
fun transactionTypeLabel(type: TransactionType): Int = when (type) {
    TransactionType.EXPENSE -> R.string.transaction_expense
    TransactionType.INCOME -> R.string.transaction_income
    TransactionType.REIMBURSEMENT -> R.string.transaction_reimbursement
}

@Composable
fun TransactionRoute(onSaved: (SavedTransaction) -> Unit, transactionId: Long? = null, onDeleted: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application, transactionId) {
        viewModelFactory { initializer { TransactionViewModel(application.repository, createSavedStateHandle(), transactionId = transactionId) } }
    }
    val viewModel: TransactionViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(state.saved?.id) { state.saved?.let(currentOnSaved) }
    LaunchedEffect(state.deleted) { if (state.deleted) currentOnDeleted() }
    TransactionScreen(state, viewModel::setType, viewModel::setAmount, viewModel::setCategory,
        viewModel::setActivity, viewModel::setReimbursable, viewModel::setDate, viewModel::setNote,
        viewModel::save, viewModel::reload, onDelete = viewModel::deleteConfirmed)
}

@Composable
fun TransactionScreen(
    state: TransactionUiState,
    onType: (TransactionType) -> Unit,
    onAmount: (String) -> Unit,
    onCategory: (Long) -> Unit,
    onActivity: (Long?) -> Unit,
    onReimbursable: (Boolean) -> Unit,
    onDate: (LocalDate) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit = {},
) {
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showActivities by rememberSaveable { mutableStateOf(false) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = !state.isSaving && !state.isDeleting && state.saved == null && !state.deleted &&
        !state.missingRecord && (state.editingId == null || !state.isLoading)
    val amountInView = remember { BringIntoViewRequester() }
    LaunchedEffect(state.amountError) {
        if (state.amountError) amountInView.bringIntoView()
    }
    if (state.missingRecord) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.transaction_missing))
        }
        return
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).imePadding().padding(16.dp)
                .testTag("transaction_form"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(selected = state.form.type == type, onClick = { onType(type) },
                        enabled = enabled, label = { Text(stringResource(transactionTypeLabel(type))) },
                        modifier = Modifier.weight(1f).testTag("type_${type.name}"))
                }
            }
            OutlinedTextField(
                value = state.form.amount, onValueChange = onAmount, enabled = enabled,
                label = { Text(stringResource(R.string.transaction_amount)) },
                prefix = { Text("¥") }, singleLine = true, isError = state.amountError,
                supportingText = { Text(stringResource(if (state.amountError) R.string.transaction_amount_error else R.string.transaction_amount_hint)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(amountInView).testTag("transaction_amount"),
            )
            OutlinedButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); showCategories = true }, enabled = enabled && !state.isLoading && !state.loadFailed,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("transaction_category")) {
                Text(stringResource(R.string.transaction_category_value,
                    state.selectedCategory?.name ?: stringResource(R.string.transaction_loading)))
            }
            OutlinedButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); showActivities = true }, enabled = enabled && !state.isLoading && !state.loadFailed,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("transaction_activity")) {
                Text(stringResource(R.string.transaction_activity_value,
                    state.selectedActivity?.name ?: stringResource(R.string.transaction_no_activity)))
            }
            if (state.form.type == TransactionType.EXPENSE) {
                Row(
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        .toggleable(state.form.reimbursable, enabled = enabled, role = Role.Switch, onValueChange = onReimbursable)
                        .testTag("transaction_reimbursable"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.transaction_reimbursable))
                    Switch(checked = state.form.reimbursable, onCheckedChange = null, enabled = enabled)
                }
            }
            OutlinedButton(onClick = {
                focusManager.clearFocus()
                keyboard?.hide()
                showDate = true
            }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("transaction_date")) {
                Text(stringResource(R.string.transaction_date_value, state.form.date.toString()))
            }
            OutlinedTextField(value = state.form.note, onValueChange = onNote, enabled = enabled,
                label = { Text(stringResource(R.string.transaction_note)) }, minLines = 2, maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("transaction_note"))
            if (state.loadFailed) {
                Text(stringResource(R.string.transaction_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.transaction_retry)) }
            }
            if (state.saveFailed) Text(stringResource(R.string.transaction_save_error), color = MaterialTheme.colorScheme.error)
            if (state.deleteFailed) Text(stringResource(R.string.transaction_delete_error), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { focusManager.clearFocus(); keyboard?.hide(); onSave() }, enabled = enabled && !state.isLoading && !state.loadFailed && state.selectedCategory != null,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("transaction_save")) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(stringResource(when {
                        state.isSaving -> R.string.transaction_saving
                        state.editingId != null -> R.string.transaction_update
                        else -> R.string.transaction_save
                    }))
                }
                if (state.editingId != null) {
                    OutlinedButton(onClick = { focusManager.clearFocus(); keyboard?.hide(); showDelete = true }, enabled = enabled && !state.loadFailed,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("transaction_delete")) {
                        Text(stringResource(if (state.isDeleting) R.string.transaction_deleting else R.string.transaction_delete),
                            color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (showCategories) {
        SelectionDialog(stringResource(R.string.transaction_choose_category),
            state.categories.map { Choice(it.id, it.name) }, state.selectedCategory?.id,
            onSelected = { id -> id?.let(onCategory); showCategories = false },
            onDismiss = { showCategories = false })
    }
    if (showActivities) {
        SelectionDialog(stringResource(R.string.transaction_choose_activity),
            listOf(Choice(null, stringResource(R.string.transaction_no_activity))) + state.activities.map { Choice(it.id, it.name) },
            state.form.activityId, onSelected = { onActivity(it); showActivities = false },
            onDismiss = { showActivities = false })
    }
    if (showDate) {
        EntryDateDialog(state.form.date, onDate, onDismiss = { showDate = false })
    }
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false },
        title = { Text(stringResource(R.string.transaction_delete_title)) },
        text = { Text(stringResource(R.string.transaction_delete_message)) },
        dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = { showDelete = false; onDelete() }, enabled = enabled,
            modifier = Modifier.testTag("transaction_delete_confirm")) {
            Text(stringResource(R.string.transaction_delete), color = MaterialTheme.colorScheme.error)
        } })
}
