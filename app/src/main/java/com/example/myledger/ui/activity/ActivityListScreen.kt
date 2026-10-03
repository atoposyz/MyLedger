package com.example.myledger.ui.activity

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.LedgerApplication
import com.example.myledger.R
import com.example.myledger.data.local.entity.ActivityType

@StringRes
fun activityTypeLabel(type: ActivityType): Int = when (type) {
    ActivityType.PERSONAL -> R.string.activity_personal
    ActivityType.WORK -> R.string.activity_work
}

@Composable
fun ActivityListRoute(onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val application = LocalContext.current.applicationContext as LedgerApplication
    val factory = remember(application) {
        viewModelFactory { initializer { ActivityListViewModel(application.repository) } }
    }
    val vm: ActivityListViewModel = viewModel(factory = factory)
    val state by vm.state.collectAsStateWithLifecycle()
    ActivityListScreen(state, onAdd, onOpen, vm::reload)
}

@Composable
fun ActivityListScreen(state: ActivityListUiState, onAdd: () -> Unit, onOpen: (Long) -> Unit,
    onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth().testTag("activities_list"),
            contentPadding = PaddingValues(16.dp)) {
            item(key = "add") {
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().testTag("activity_add")) {
                    Text(stringResource(R.string.activity_create))
                }
                Spacer(Modifier.height(16.dp))
            }
            when {
                state.isLoading -> item { CircularProgressIndicator(Modifier.testTag("activities_loading")) }
                state.loadFailed -> item {
                    Text(stringResource(R.string.activity_load_error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.transaction_retry)) }
                }
                state.activities.isEmpty() -> item {
                    Text(stringResource(R.string.activities_empty), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.activities_hint), modifier = Modifier.padding(top = 8.dp))
                }
                else -> items(state.activities, key = { it.id }) { activity ->
                    ListItem(modifier = Modifier.clickable(role = Role.Button, onClick = { onOpen(activity.id) })
                        .testTag("activity_${activity.id}"),
                        headlineContent = { Text(activity.name) },
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(activityTypeLabel(activity.type)))
                                if (activity.startDate != null || activity.endDate != null) {
                                    Text(stringResource(R.string.activity_dates,
                                        activity.startDate?.toString() ?: stringResource(R.string.activity_unset),
                                        activity.endDate?.toString() ?: stringResource(R.string.activity_unset)))
                                }
                                activity.note?.takeIf(String::isNotBlank)?.let {
                                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        })
                    HorizontalDivider()
                }
            }
        }
    }
}
