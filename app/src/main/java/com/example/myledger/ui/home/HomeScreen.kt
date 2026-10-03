package com.example.myledger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.data.local.entity.TransactionType
import com.example.myledger.ui.activity.activityTypeLabel
import com.example.myledger.ui.transaction.transactionTypeLabel
import com.example.myledger.util.LedgerDates
import com.example.myledger.util.MoneyInput
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun HomeRoute(onOpenRecord: (Long) -> Unit, onRecords: () -> Unit, onActivities: () -> Unit) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application) {
        viewModelFactory { initializer { HomeViewModel(application.repository) } }
    }
    val vm: HomeViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { vm.refreshMonth(); delay(60_000) }
        }
    }
    HomeScreen(state, onOpenRecord, onRecords, onActivities, vm::reload)
}

@Composable
fun HomeScreen(state: HomeUiState, onOpenRecord: (Long) -> Unit, onRecords: () -> Unit,
    onActivities: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalResources.current.configuration.locales[0]
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("home_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
            item(key = "month") {
                Text(LedgerDates.formatMonth(state.month, locale), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() }.testTag("home_month"))
                Spacer(Modifier.height(20.dp))
            }
            when {
                state.isLoading -> item { CircularProgressIndicator(Modifier.testTag("home_loading")) }
                state.loadFailed || state.summary == null -> item {
                    Text(stringResource(R.string.home_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("home_retry")) { Text(stringResource(R.string.transaction_retry)) }
                }
                else -> {
                    val overview = state.summary
                    val period = overview.period
                    item(key = "summary") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.home_balance), style = MaterialTheme.typography.titleSmall)
                            Text(overview.balanceMinor?.let(MoneyInput::formatCurrencyMinor) ?: stringResource(R.string.home_unavailable),
                                style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("home_balance"))
                            Text(stringResource(R.string.home_balance_formula), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            HomeAmountRow(stringResource(R.string.home_ordinary_income), period?.income?.ordinaryMinor, "home_ordinary_income")
                            HomeAmountRow(stringResource(R.string.home_reimbursement), period?.income?.reimbursementMinor, "home_reimbursement")
                            HomeAmountRow(stringResource(R.string.home_daily_expense), period?.expense?.dailyMinor, "home_daily_expense")
                            HomeAmountRow(stringResource(R.string.home_all_expense), period?.expense?.totalMinor, "home_all_expense")
                            Text(stringResource(R.string.home_daily_hint), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (period == null) Text(stringResource(R.string.home_summary_overflow), color = MaterialTheme.colorScheme.error)
                            else if (overview.balanceMinor == null) Text(stringResource(R.string.home_balance_overflow), color = MaterialTheme.colorScheme.error)
                            if (overview.recordCount == 0) Text(stringResource(R.string.home_month_empty), style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(24.dp))
                        HorizontalDivider()
                    }
                    item(key = "activities_heading") {
                        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text(stringResource(R.string.home_activity_expenses), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            TextButton(onClick = onActivities, modifier = Modifier.testTag("home_activities")) { Text(stringResource(R.string.home_manage_activities)) }
                        }
                        if (overview.activityExpenses.isEmpty()) Text(stringResource(R.string.home_activities_empty),
                            modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(overview.activityExpenses, key = { "activity_${it.activityId}" }) { expense ->
                        val activity = state.activityById[expense.activityId]
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("home_activity_${expense.activityId}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            HomeAmountRow(activity?.name ?: stringResource(R.string.home_unknown_activity), expense.amountMinor)
                            Text(stringResource(R.string.home_activity_count, expense.recordCount,
                                activity?.type?.let { stringResource(activityTypeLabel(it)) } ?: stringResource(R.string.home_unknown_activity)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    item(key = "recent_heading") {
                        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                            Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.semantics { heading() })
                            TextButton(onClick = onRecords, modifier = Modifier.testTag("home_records")) { Text(stringResource(R.string.home_view_records)) }
                        }
                        Text(stringResource(R.string.home_recent_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.recentRecords.isEmpty()) Text(stringResource(R.string.home_recent_empty), modifier = Modifier.padding(vertical = 12.dp))
                    }
                    items(state.recentRecords, key = { "record_${it.transaction.id}" }) { item ->
                        val record = item.transaction
                        Column(Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp)
                            .clickable(role = Role.Button, onClick = { onOpenRecord(record.id) })
                            .padding(vertical = 12.dp).testTag("home_record_${record.id}"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(item.categoryName ?: stringResource(R.string.records_unknown_category), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text((if (record.type == TransactionType.EXPENSE) "−¥" else "+¥") + MoneyInput.formatMinor(record.amountMinor),
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(stringResource(R.string.home_record_context, LedgerDates.formatDay(record.date, locale),
                                stringResource(transactionTypeLabel(record.type))), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            record.note?.takeIf(String::isNotBlank)?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                            item.activityName?.let { Text(stringResource(R.string.transaction_activity_value, it), style = MaterialTheme.typography.bodySmall) }
                            if (record.reimbursable) Text(stringResource(R.string.transaction_reimbursable), style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeAmountRow(label: String, amount: Long?, tag: String? = null) {
    val text = amount?.let(MoneyInput::formatCurrencyMinor) ?: stringResource(R.string.home_unavailable)
    val amountStyle = MaterialTheme.typography.titleMedium
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth().then(if (tag == null) Modifier else Modifier.testTag(tag))
        .semantics(mergeDescendants = true) {}) {
        if (measurer.measure(text, amountStyle).size.width > constraints.maxWidth / 2) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(text, style = amountStyle)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Text(text, style = amountStyle)
            }
        }
    }
}
