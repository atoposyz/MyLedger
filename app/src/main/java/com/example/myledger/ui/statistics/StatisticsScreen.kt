package com.example.myledger.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.analysis.model.ExpenseScope
import com.example.myledger.util.LedgerDates
import com.example.myledger.util.MoneyInput
import com.example.myledger.util.StatisticsFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun StatisticsRoute() {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application) {
        viewModelFactory { initializer { StatisticsViewModel(application.repository, createSavedStateHandle()) } }
    }
    val vm: StatisticsViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(vm, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { vm.refreshMonth(); delay(60_000) }
        }
    }
    StatisticsScreen(state, vm::setScope, vm::reload)
}

@Composable
fun StatisticsScreen(state: StatisticsUiState, onScope: (ExpenseScope) -> Unit, onRetry: () -> Unit,
    modifier: Modifier = Modifier) {
    val locale = LocalResources.current.configuration.locales[0]
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("statistics_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp)) {
            item(key = "controls") {
                Text(LedgerDates.formatMonth(state.month, locale), style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag("statistics_month").semantics { heading() })
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 12.dp)) {
                    ExpenseScope.entries.forEach { scope ->
                        FilterChip(selected = state.scope == scope, onClick = { onScope(scope) },
                            label = { Text(stringResource(if (scope == ExpenseScope.DAILY) R.string.statistics_daily else R.string.statistics_all)) },
                            modifier = Modifier.testTag("statistics_scope_${scope.name}"))
                    }
                }
                Text(stringResource(if (state.scope == ExpenseScope.DAILY) R.string.statistics_daily_hint else R.string.statistics_all_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(20.dp))
            }
            when {
                state.isLoading -> item { CircularProgressIndicator(Modifier.testTag("statistics_loading")) }
                state.loadFailed || state.summary == null -> item {
                    Text(stringResource(R.string.statistics_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("statistics_retry")) { Text(stringResource(R.string.transaction_retry)) }
                }
                else -> {
                    val summary = state.summary
                    item(key = "total") {
                        Text(stringResource(R.string.statistics_month_total), style = MaterialTheme.typography.titleSmall)
                        Text(summary.totalMinor?.let(MoneyInput::formatCurrencyMinor) ?: stringResource(R.string.home_unavailable),
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("statistics_total"))
                        if (summary.totalMinor == null) Text(stringResource(R.string.statistics_overflow), color = MaterialTheme.colorScheme.error)
                        else if (summary.totalMinor == 0L) Text(stringResource(R.string.statistics_month_empty), modifier = Modifier.padding(top = 8.dp))
                        Spacer(Modifier.height(24.dp)); HorizontalDivider()
                    }
                    item(key = "categories_heading") {
                        Text(stringResource(R.string.statistics_categories), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).semantics { heading() })
                        Text(stringResource(R.string.statistics_category_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (summary.totalMinor == null) Text(stringResource(R.string.statistics_categories_unavailable), modifier = Modifier.padding(vertical = 12.dp))
                        else if (summary.categories.isEmpty()) Text(stringResource(R.string.statistics_categories_empty), modifier = Modifier.padding(vertical = 12.dp))
                    }
                    itemsIndexed(summary.categories, key = { _, it -> "category_${it.categoryId}" }) { index, category ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("statistics_category_${category.categoryId}")
                            .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.statistics_rank, index + 1, state.categoryNames[category.categoryId]
                                ?: stringResource(R.string.records_unknown_category)), fontWeight = FontWeight.Bold)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(MoneyInput.formatCurrencyMinor(category.amountMinor), modifier = Modifier.weight(1f))
                                Text(StatisticsFormatter.percentage(category.amountMinor, requireNotNull(summary.totalMinor)))
                            }
                            ExpenseBar(StatisticsFormatter.share(category.amountMinor, requireNotNull(summary.totalMinor)))
                            Text(stringResource(R.string.statistics_count, category.transactionCount), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider()
                    }
                    item(key = "trend_heading") {
                        Text(stringResource(R.string.statistics_trend), style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).testTag("statistics_trend").semantics { heading() })
                        Text(stringResource(R.string.statistics_trend_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(summary.trend, key = { "trend_${it.month}" }) { point ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("statistics_trend_${point.month}")
                            .semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(LedgerDates.formatMonth(point.month, locale), style = MaterialTheme.typography.bodySmall)
                            Text(point.amountMinor?.let(MoneyInput::formatCurrencyMinor) ?: stringResource(R.string.home_unavailable),
                                style = MaterialTheme.typography.titleMedium)
                            if (point.amountMinor == null) Text(stringResource(R.string.statistics_trend_overflow), color = MaterialTheme.colorScheme.error)
                            else ExpenseBar(StatisticsFormatter.share(point.amountMinor, summary.trendMaximumMinor))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant).clearAndSetSemantics {}) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
    }
}
