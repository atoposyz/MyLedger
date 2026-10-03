package com.example.myledger.ui.batchentry

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.myledger.ui.transaction.transactionTypeLabel
import com.example.myledger.util.MoneyInput
import java.time.LocalDate

@Composable
fun BatchEntryRoute(onSaved: (SavedBatch) -> Unit) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application) {
        viewModelFactory { initializer { BatchEntryViewModel(application.repository, createSavedStateHandle()) } }
    }
    val vm: BatchEntryViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(state.saved) { state.saved?.let(currentOnSaved) }
    BatchDailyEntryScreen(state, vm::setDefaultDate, vm::setDefaultActivity, vm::setDefaultReimbursable,
        vm::addDraft, vm::removeDraft, vm::setType, vm::setAmount, vm::setCategory, vm::setActivity,
        vm::setReimbursable, vm::setDate, vm::setNote, vm::saveAll, vm::reload)
}

@Composable
fun BatchDailyEntryScreen(
    state: BatchEntryUiState,
    onDefaultDate: (LocalDate) -> Unit,
    onDefaultActivity: (Long?) -> Unit,
    onDefaultReimbursable: (Boolean) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onType: (Long, TransactionType) -> Unit,
    onAmount: (Long, String) -> Unit,
    onCategory: (Long, Long) -> Unit,
    onActivity: (Long, Long?) -> Unit,
    onReimbursable: (Long, Boolean) -> Unit,
    onDate: (Long, LocalDate) -> Unit,
    onNote: (Long, String) -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = rememberLazyListState()
    var defaultDateDialog by rememberSaveable { mutableStateOf(false) }
    var defaultActivityDialog by rememberSaveable { mutableStateOf(false) }
    val enabled = !state.isSaving && state.saved == null
    val choicesReady = enabled && !state.isLoading && !state.loadFailed
    val total = state.totalMinor
    var previousCount by remember { mutableIntStateOf(state.drafts.size) }
    LaunchedEffect(state.drafts.size) {
        if (state.drafts.size > previousCount) list.scrollToItem(state.drafts.size)
        previousCount = state.drafts.size
    }
    LaunchedEffect(state.invalidAmountIds) {
        val index = state.drafts.indexOfFirst { it.id in state.invalidAmountIds }
        if (index >= 0) list.scrollToItem(index + 1)
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(state = list, modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().testTag("batch_list"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "defaults") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.batch_defaults), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.batch_defaults_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { defaultDateDialog = true }, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().testTag("batch_default_date")) {
                        Text(stringResource(R.string.transaction_date_value, state.defaults.date.toString()))
                    }
                    OutlinedButton(onClick = { defaultActivityDialog = true }, enabled = choicesReady,
                        modifier = Modifier.fillMaxWidth().testTag("batch_default_activity")) {
                        Text(stringResource(R.string.batch_default_activity,
                            state.activities.firstOrNull { it.id == state.defaults.activityId }?.name
                                ?: stringResource(R.string.transaction_no_activity)))
                    }
                    if (state.defaultIsWork) ReimbursableToggle(state.defaults.reimbursable, enabled,
                        stringResource(R.string.batch_default_reimbursable), onDefaultReimbursable,
                        Modifier.testTag("batch_default_reimbursable"))
                    if (state.loadFailed) {
                        Text(stringResource(R.string.transaction_load_error), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry) { Text(stringResource(R.string.transaction_retry)) }
                    }
                }
            }
            itemsIndexed(state.drafts, key = { _, draft -> draft.id }) { index, draft ->
                DraftRow(state, draft, index + 1, enabled, choicesReady, onRemove, onType, onAmount,
                    onCategory, onActivity, onReimbursable, onDate, onNote)
            }
            item(key = "save") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (state.drafts.isEmpty()) Text(stringResource(R.string.batch_no_drafts))
                    OutlinedButton(onClick = onAdd, enabled = choicesReady,
                        modifier = Modifier.fillMaxWidth().testTag("batch_add")) { Text(stringResource(R.string.batch_add)) }
                    Text(stringResource(R.string.batch_total, state.drafts.size,
                        total?.let(MoneyInput::formatMinor) ?: stringResource(R.string.batch_overflow_value)),
                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("batch_total"))
                    Text(stringResource(R.string.batch_total_hint), style = MaterialTheme.typography.bodySmall)
                    if (total == null) Text(stringResource(R.string.batch_total_overflow), color = MaterialTheme.colorScheme.error)
                    if (state.saveFailed) Text(stringResource(R.string.batch_save_error), color = MaterialTheme.colorScheme.error)
                    Button(onClick = onSave,
                        enabled = choicesReady && state.drafts.isNotEmpty() && total != null,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("batch_save")) {
                        if (state.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (state.isSaving) R.string.transaction_saving else R.string.batch_save))
                    }
                }
            }
        }
    }
    if (defaultDateDialog) EntryDateDialog(state.defaults.date, onDefaultDate, onDismiss = { defaultDateDialog = false })
    if (defaultActivityDialog) SelectionDialog(stringResource(R.string.transaction_choose_activity),
        listOf(Choice(null, stringResource(R.string.transaction_no_activity))) + state.activities.map { Choice(it.id, it.name) },
        state.defaults.activityId, onSelected = { onDefaultActivity(it); defaultActivityDialog = false },
        onDismiss = { defaultActivityDialog = false })
}

@Composable
private fun DraftRow(state: BatchEntryUiState, draft: TransactionDraft, number: Int,
    enabled: Boolean, choicesReady: Boolean,
    onRemove: (Long) -> Unit, onType: (Long, TransactionType) -> Unit,
    onAmount: (Long, String) -> Unit, onCategory: (Long, Long) -> Unit,
    onActivity: (Long, Long?) -> Unit, onReimbursable: (Long, Boolean) -> Unit,
    onDate: (Long, LocalDate) -> Unit, onNote: (Long, String) -> Unit,
) {
    var categoriesDialog by rememberSaveable { mutableStateOf(false) }
    var activityDialog by rememberSaveable { mutableStateOf(false) }
    var dateDialog by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf(false) }
    val category = state.selectedCategory(draft)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("batch_row_${draft.id}")) {
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.batch_row_title, number), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = { onRemove(draft.id) }, enabled = enabled, modifier = Modifier.testTag("batch_remove_${draft.id}")) {
                Text(stringResource(R.string.batch_remove))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TransactionType.entries.forEach { type ->
                FilterChip(selected = draft.type == type, onClick = { onType(draft.id, type) }, enabled = enabled,
                    label = { Text(stringResource(transactionTypeLabel(type))) },
                    modifier = Modifier.weight(1f).testTag("batch_type_${draft.id}_${type.name}"))
            }
        }
        OutlinedTextField(value = draft.amount, onValueChange = { onAmount(draft.id, it) }, enabled = enabled,
            label = { Text(stringResource(R.string.transaction_amount)) }, prefix = { Text("¥") }, singleLine = true,
            isError = draft.id in state.invalidAmountIds,
            supportingText = { if (draft.id in state.invalidAmountIds) Text(stringResource(R.string.transaction_amount_error)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().testTag("batch_amount_${draft.id}"))
        OutlinedButton(onClick = { categoriesDialog = true }, enabled = choicesReady, modifier = Modifier.fillMaxWidth().testTag("batch_category_${draft.id}")) {
            Text(stringResource(R.string.transaction_category_value, category?.name ?: stringResource(R.string.transaction_loading)))
        }
        OutlinedTextField(value = draft.note, onValueChange = { onNote(draft.id, it) }, enabled = enabled,
            label = { Text(stringResource(R.string.transaction_note)) }, minLines = 1, maxLines = 3,
            modifier = Modifier.fillMaxWidth().testTag("batch_note_${draft.id}"))
        TextButton(onClick = { details = !details }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).testTag("batch_details_${draft.id}")) {
            Text(stringResource(R.string.transaction_date_value, draft.date.toString()), modifier = Modifier.weight(1f))
            Text(stringResource(if (details) R.string.batch_less_options else R.string.batch_more_options))
        }
        if (!details && (draft.activityId != null || draft.reimbursable)) {
            Text(stringResource(R.string.batch_row_context,
                state.activities.firstOrNull { it.id == draft.activityId }?.name ?: stringResource(R.string.transaction_no_activity),
                stringResource(if (draft.reimbursable) R.string.transaction_reimbursable else R.string.batch_not_reimbursable)),
                style = MaterialTheme.typography.bodySmall)
        }
        if (details) {
            OutlinedButton(onClick = { dateDialog = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("batch_date_${draft.id}")) {
                Text(stringResource(R.string.transaction_date_value, draft.date.toString()))
            }
            OutlinedButton(onClick = { activityDialog = true }, enabled = choicesReady, modifier = Modifier.fillMaxWidth().testTag("batch_activity_${draft.id}")) {
                Text(stringResource(R.string.transaction_activity_value,
                    state.activities.firstOrNull { it.id == draft.activityId }?.name ?: stringResource(R.string.transaction_no_activity)))
            }
            if (draft.type == TransactionType.EXPENSE) ReimbursableToggle(draft.reimbursable, enabled,
                stringResource(R.string.transaction_reimbursable), { onReimbursable(draft.id, it) },
                Modifier.testTag("batch_reimbursable_${draft.id}"))
        }
    }
    if (categoriesDialog) SelectionDialog(stringResource(R.string.transaction_choose_category),
        state.categories.filter { it.type == draft.type }.map { Choice(it.id, it.name) }, category?.id,
        onSelected = { it?.let { id -> onCategory(draft.id, id) }; categoriesDialog = false },
        onDismiss = { categoriesDialog = false })
    if (activityDialog) SelectionDialog(stringResource(R.string.transaction_choose_activity),
        listOf(Choice(null, stringResource(R.string.transaction_no_activity))) + state.activities.map { Choice(it.id, it.name) },
        draft.activityId, onSelected = { onActivity(draft.id, it); activityDialog = false }, onDismiss = { activityDialog = false })
    if (dateDialog) EntryDateDialog(draft.date, { onDate(draft.id, it) }, onDismiss = { dateDialog = false })
}

@Composable
private fun ReimbursableToggle(checked: Boolean, enabled: Boolean, label: String,
    onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
