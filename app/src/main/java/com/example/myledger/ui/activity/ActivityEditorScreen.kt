package com.example.myledger.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.data.local.entity.ActivityType
import com.example.myledger.ui.components.EntryDateDialog
import java.time.LocalDate

@Composable
fun ActivityEditorRoute(onSaved: () -> Unit, onDeleted: () -> Unit, activityId: Long? = null) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application, activityId) {
        viewModelFactory { initializer { ActivityEditorViewModel(application.repository, createSavedStateHandle(), activityId) } }
    }
    val vm: ActivityEditorViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val currentOnSaved by rememberUpdatedState(onSaved)
    val currentOnDeleted by rememberUpdatedState(onDeleted)
    LaunchedEffect(state.savedId) { if (state.savedId != null) currentOnSaved() }
    LaunchedEffect(state.deleted) { if (state.deleted) currentOnDeleted() }
    ActivityEditorScreen(state, vm::setName, vm::setType, vm::setStart, vm::setEnd, vm::setNote,
        vm::save, vm::reload, vm::requestDelete, vm::dismissDelete, vm::deleteConfirmed)
}

@Composable
fun ActivityEditorScreen(state: ActivityEditorUiState, onName: (String) -> Unit, onType: (ActivityType) -> Unit,
    onStart: (LocalDate?) -> Unit, onEnd: (LocalDate?) -> Unit, onNote: (String) -> Unit,
    onSave: () -> Unit, onRetry: () -> Unit, onRequestDelete: () -> Unit, onDismissDelete: () -> Unit,
    onDeleteConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    var picker by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = !state.busy && !state.missing && !state.loadFailed && state.deleteUsage == null
    val nameInView = remember { BringIntoViewRequester() }
    LaunchedEffect(state.nameError) { if (state.nameError) nameInView.bringIntoView() }
    if (state.missing) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.activity_missing))
        }
        return
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState())
            .imePadding().padding(16.dp).testTag("activity_form"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(value = state.form.name, onValueChange = onName, enabled = enabled,
                singleLine = true, label = { Text(stringResource(R.string.activity_name)) }, isError = state.nameError,
                supportingText = { if (state.nameError) Text(stringResource(R.string.activity_name_error)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().bringIntoViewRequester(nameInView).testTag("activity_name"))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityType.entries.forEach { type ->
                    FilterChip(selected = state.form.type == type, onClick = { onType(type) }, enabled = enabled,
                        label = { Text(stringResource(activityTypeLabel(type))) },
                        modifier = Modifier.weight(1f).testTag("activity_type_${type.name}"))
                }
            }
            Text(stringResource(R.string.activity_optional_dates), style = MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { focus.clearFocus(); keyboard?.hide(); picker = 1 }, enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("activity_start")) {
                    Text(stringResource(R.string.activity_start, state.form.startDate?.toString() ?: stringResource(R.string.activity_unset)))
                }
                if (state.form.startDate != null) TextButton(onClick = { onStart(null) }, enabled = enabled,
                    modifier = Modifier.testTag("activity_clear_start")) { Text(stringResource(R.string.activity_clear_date)) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { focus.clearFocus(); keyboard?.hide(); picker = 2 }, enabled = enabled,
                    modifier = Modifier.weight(1f).testTag("activity_end")) {
                    Text(stringResource(R.string.activity_end, state.form.endDate?.toString() ?: stringResource(R.string.activity_unset)))
                }
                if (state.form.endDate != null) TextButton(onClick = { onEnd(null) }, enabled = enabled,
                    modifier = Modifier.testTag("activity_clear_end")) { Text(stringResource(R.string.activity_clear_date)) }
            }
            if (state.form.invalidDates) Text(stringResource(R.string.records_invalid_range), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(value = state.form.note, onValueChange = onNote, enabled = enabled, minLines = 2, maxLines = 5,
                label = { Text(stringResource(R.string.transaction_note)) }, modifier = Modifier.fillMaxWidth().testTag("activity_note"))
            if (state.isLoading) CircularProgressIndicator(Modifier.testTag("activity_loading"))
            if (state.loadFailed) {
                Text(stringResource(R.string.activity_load_error), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry) { Text(stringResource(R.string.transaction_retry)) }
            }
            if (state.saveFailed) Text(stringResource(R.string.activity_save_error), color = MaterialTheme.colorScheme.error)
            if (state.deleteFailed && state.deleteUsage == null) Text(stringResource(R.string.transaction_delete_error), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { focus.clearFocus(); keyboard?.hide(); onSave() }, enabled = enabled && !state.form.invalidDates,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("activity_save")) {
                    Text(stringResource(if (state.isSaving) R.string.transaction_saving else R.string.transaction_save))
                }
                if (state.editingId != null) OutlinedButton(onClick = { focus.clearFocus(); keyboard?.hide(); onRequestDelete() }, enabled = enabled,
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp).testTag("activity_delete")) {
                    Text(stringResource(R.string.activity_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (picker != 0) EntryDateDialog((if (picker == 1) state.form.startDate else state.form.endDate) ?: LocalDate.now(),
        onSelected = { if (picker == 1) onStart(it) else onEnd(it) }, onDismiss = { picker = 0 })
    state.deleteUsage?.let { count ->
        AlertDialog(onDismissRequest = onDismissDelete,
            title = { Text(stringResource(if (count > 0) R.string.activity_in_use_title else R.string.activity_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (count > 0) stringResource(R.string.activity_in_use_message, count)
                        else stringResource(R.string.activity_delete_message))
                    if (state.deleteFailed) Text(stringResource(R.string.transaction_delete_error), color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = if (count > 0) onDismissDelete else onDeleteConfirmed, enabled = !state.isDeleting,
                    modifier = Modifier.testTag("activity_delete_confirm")) {
                    Text(stringResource(if (count > 0) R.string.activity_understood else R.string.activity_delete))
                }
            }, dismissButton = {
                if (count == 0L) TextButton(onClick = onDismissDelete, enabled = !state.isDeleting) { Text(stringResource(R.string.cancel)) }
            })
    }
}
