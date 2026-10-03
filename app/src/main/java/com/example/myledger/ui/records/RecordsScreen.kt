package com.example.myledger.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.ui.components.EntryDateDialog
import com.example.myledger.ui.transaction.transactionTypeLabel
import com.example.myledger.util.LedgerDates
import com.example.myledger.util.MoneyInput
import java.time.LocalDate

@Composable
fun RecordsRoute(onOpen: (Long) -> Unit) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application) {
        viewModelFactory { initializer { RecordsViewModel(application.repository, createSavedStateHandle()) } }
    }
    val vm: RecordsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    RecordsScreen(state, onOpen, vm::setRange, vm::reload)
}

@Composable
fun RecordsScreen(state: RecordsUiState, onOpen: (Long) -> Unit, onRange: (RecordDateRange?) -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    var showRange by rememberSaveable { mutableStateOf(false) }
    val locale = LocalResources.current.configuration.locales[0]
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("records_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp)) {
            item(key = "filter") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { showRange = true }, modifier = Modifier.weight(1f).testTag("records_filter")) {
                        Text(state.range?.let { stringResource(R.string.records_range_value, it.start.toString(), it.end.toString()) }
                            ?: stringResource(R.string.records_range_all))
                    }
                    if (state.range != null) TextButton(onClick = { onRange(null) }, modifier = Modifier.testTag("records_clear_filter")) {
                        Text(stringResource(R.string.records_clear_filter))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            when {
                state.isLoading -> item(key = "loading") { CircularProgressIndicator(Modifier.testTag("records_loading")) }
                state.loadFailed -> item(key = "error") {
                    Text(stringResource(R.string.records_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.transaction_retry)) }
                }
                state.days.isEmpty() -> item(key = "empty") {
                    Text(stringResource(if (state.range == null) R.string.records_empty_title else R.string.records_range_empty),
                        style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.records_empty_message), modifier = Modifier.padding(top = 8.dp))
                }
                else -> state.days.forEach { day ->
                    item(key = "day_${day.date.toEpochDay()}") {
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("records_day_${day.date}")) {
                            Text(LedgerDates.formatDay(day.date, locale), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            val summary = day.summary
                            if (summary == null) Text(stringResource(R.string.records_summary_overflow), color = MaterialTheme.colorScheme.error)
                            else {
                                Text(stringResource(R.string.records_daily_expense, MoneyInput.formatMinor(summary.expense.totalMinor)),
                                    style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.records_daily_income, MoneyInput.formatMinor(summary.income.ordinaryMinor),
                                    MoneyInput.formatMinor(summary.income.reimbursementMinor)), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    items(day.records, key = { "record_${it.transaction.id}" }) { item ->
                        val record = item.transaction
                        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                            .clickable(role = Role.Button, onClick = { onOpen(record.id) })
                            .padding(vertical = 12.dp).testTag("record_${record.id}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(item.categoryName ?: stringResource(R.string.records_unknown_category),
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text((if (record.type == TransactionType.EXPENSE) "−¥" else "+¥") + MoneyInput.formatMinor(record.amountMinor),
                                    style = MaterialTheme.typography.titleSmall)
                            }
                            Text(stringResource(transactionTypeLabel(record.type)), style = MaterialTheme.typography.labelMedium)
                            record.note?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            if (item.activityName != null) Text(stringResource(R.string.transaction_activity_value, item.activityName),
                                style = MaterialTheme.typography.bodySmall)
                            if (record.reimbursable) Text(stringResource(R.string.transaction_reimbursable), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    if (showRange) RecordRangeDialog(state.range, onApply = { onRange(it); showRange = false }, onDismiss = { showRange = false })
}

@Composable
private fun RecordRangeDialog(range: RecordDateRange?, onApply: (RecordDateRange) -> Unit, onDismiss: () -> Unit) {
    var start by rememberSaveable { mutableLongStateOf((range?.start ?: LocalDate.now().withDayOfMonth(1)).toEpochDay()) }
    var end by rememberSaveable { mutableLongStateOf((range?.end ?: LocalDate.now()).toEpochDay()) }
    var picker by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.records_choose_range)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { picker = 1 }, modifier = Modifier.fillMaxWidth().testTag("records_range_start")) {
                Text(stringResource(R.string.records_start_date, LocalDate.ofEpochDay(start).toString()))
            }
            OutlinedButton(onClick = { picker = 2 }, modifier = Modifier.fillMaxWidth().testTag("records_range_end")) {
                Text(stringResource(R.string.records_end_date, LocalDate.ofEpochDay(end).toString()))
            }
            if (end < start) Text(stringResource(R.string.records_invalid_range), color = MaterialTheme.colorScheme.error)
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        confirmButton = { TextButton(onClick = { onApply(RecordDateRange(LocalDate.ofEpochDay(start), LocalDate.ofEpochDay(end))) },
            enabled = end >= start, modifier = Modifier.testTag("records_range_apply")) { Text(stringResource(R.string.records_apply_range)) } })
    if (picker != 0) EntryDateDialog(LocalDate.ofEpochDay(if (picker == 1) start else end),
        onSelected = { if (picker == 1) start = it.toEpochDay() else end = it.toEpochDay() }, onDismiss = { picker = 0 })
}
