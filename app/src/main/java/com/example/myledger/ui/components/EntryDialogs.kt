package com.example.myledger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.myledger.R
import com.example.myledger.util.LedgerDates
import java.time.LocalDate

internal data class Choice(val id: Long?, val label: String)

@Composable
internal fun SelectionDialog(title: String, choices: List<Choice>, selectedId: Long?,
    onSelected: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                choices.forEach { choice ->
                    Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                        .selectable(choice.id == selectedId, role = Role.RadioButton, onClick = { onSelected(choice.id) }),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadioButton(selected = choice.id == selectedId, onClick = null)
                        Text(choice.label)
                    }
                }
            }
        })
}

@Composable
internal fun EntryDateDialog(date: LocalDate, onSelected: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val compactHeight = LocalResources.current.configuration.screenHeightDp < 480
    val picker = rememberDatePickerState(initialSelectedDateMillis = LedgerDates.toPickerMillis(date),
        initialDisplayMode = if (compactHeight) DisplayMode.Input else DisplayMode.Picker)
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onSelected(LedgerDates.fromPickerMillis(it)) }
                onDismiss()
            }) { Text(stringResource(R.string.transaction_confirm)) }
        }, dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }) { DatePicker(state = picker) }
}
