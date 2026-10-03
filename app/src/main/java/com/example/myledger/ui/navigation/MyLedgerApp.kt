package com.example.myledger.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myledger.R
import com.example.myledger.ui.activity.ActivityListRoute
import com.example.myledger.ui.activity.ActivityEditorRoute
import com.example.myledger.ui.batchentry.BatchEntryRoute
import com.example.myledger.ui.home.HomeRoute
import com.example.myledger.ui.records.RecordsRoute
import com.example.myledger.ui.settings.SettingsScreen
import com.example.myledger.ui.statistics.StatisticsRoute
import com.example.myledger.ui.transaction.TransactionRoute
import com.example.myledger.ui.transaction.transactionTypeLabel
import com.example.myledger.util.MoneyInput
import kotlinx.coroutines.launch

@Composable
fun MyLedgerApp() {
    val navController = rememberNavController()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = LedgerDestination.entries.firstOrNull {
        it.route == backStackEntry?.destination?.route
    } ?: LedgerDestination.HOME
    val isMainDestination = currentDestination in mainDestinations
    val showReceipts = isMainDestination || currentDestination == LedgerDestination.ACTIVITIES
    LaunchedEffect(showReceipts, snackbar.currentSnackbarData) {
        if (!showReceipts) snackbar.currentSnackbarData?.dismiss()
    }
    var showEntryOptions by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { if (showReceipts) SnackbarHost(snackbar) },
        topBar = {
            Surface {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .defaultMinSize(minHeight = 56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!isMainDestination) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                    Text(
                        text = stringResource(currentDestination.title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.semantics { heading() }
                    )
                }
            }
        },
        bottomBar = {
            if (isMainDestination) {
                NavigationBar {
                    mainDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentDestination == destination,
                            onClick = {
                                if (currentDestination != destination) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(requireNotNull(destination.icon)),
                                    contentDescription = null
                                )
                            },
                            label = { Text(stringResource(destination.title)) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isMainDestination) {
                FloatingActionButton(onClick = { showEntryOptions = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.add_transaction)
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = LedgerDestination.HOME.route,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .fillMaxSize(),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            composable(LedgerDestination.HOME.route) {
                HomeRoute(onOpenRecord = { id -> navController.navigate("edit_transaction/$id") { launchSingleTop = true } },
                    onActivities = { navController.navigate(LedgerDestination.ACTIVITIES.route) { launchSingleTop = true } },
                    onRecords = {
                        navController.navigate(LedgerDestination.RECORDS.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    })
            }
            composable(LedgerDestination.RECORDS.route) {
                RecordsRoute(onOpen = { id -> navController.navigate("edit_transaction/$id") { launchSingleTop = true } })
            }
            composable(LedgerDestination.STATISTICS.route) { StatisticsRoute() }
            composable(LedgerDestination.SETTINGS.route) {
                SettingsScreen(onOpenActivities = { navController.navigate(LedgerDestination.ACTIVITIES.route) { launchSingleTop = true } })
            }
            composable(LedgerDestination.ACTIVITIES.route) {
                ActivityListRoute(onAdd = { navController.navigate(LedgerDestination.CREATE_ACTIVITY.route) { launchSingleTop = true } },
                    onOpen = { id -> navController.navigate("edit_activity/$id") { launchSingleTop = true } })
            }
            composable(LedgerDestination.CREATE_ACTIVITY.route) {
                ActivityEditorRoute(onSaved = {
                    navController.popBackStack()
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.activity_saved)) }
                }, onDeleted = {})
            }
            composable(LedgerDestination.EDIT_ACTIVITY.route,
                arguments = listOf(navArgument("activityId") { type = NavType.LongType })) { entry ->
                ActivityEditorRoute(activityId = requireNotNull(entry.arguments).getLong("activityId"), onSaved = {
                    navController.popBackStack()
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.activity_saved)) }
                }, onDeleted = {
                    navController.popBackStack()
                    scope.launch { snackbar.showSnackbar(resources.getString(R.string.activity_deleted)) }
                })
            }
            composable(LedgerDestination.SINGLE_ENTRY.route) {
                TransactionRoute(onSaved = { saved ->
                    val message = resources.getString(R.string.transaction_saved_receipt,
                        resources.getString(transactionTypeLabel(saved.type)), MoneyInput.formatMinor(saved.amountMinor),
                        saved.categoryName, saved.date.toString())
                    navController.popBackStack()
                    scope.launch { snackbar.showSnackbar(message) }
                })
            }
            composable(LedgerDestination.BATCH_ENTRY.route) {
                BatchEntryRoute(onSaved = { saved ->
                    val message = resources.getString(R.string.batch_saved_receipt,
                        saved.count, MoneyInput.formatMinor(saved.totalMinor))
                    navController.popBackStack()
                    scope.launch { snackbar.showSnackbar(message) }
                })
            }
            composable(LedgerDestination.EDIT_TRANSACTION.route,
                arguments = listOf(navArgument("transactionId") { type = NavType.LongType })) { entry ->
                TransactionRoute(transactionId = requireNotNull(entry.arguments).getLong("transactionId"),
                    onSaved = { saved ->
                        val message = resources.getString(R.string.transaction_updated_receipt,
                            resources.getString(transactionTypeLabel(saved.type)), MoneyInput.formatMinor(saved.amountMinor),
                            saved.categoryName, saved.date.toString())
                        navController.popBackStack()
                        scope.launch { snackbar.showSnackbar(message) }
                    }, onDeleted = {
                        navController.popBackStack()
                        scope.launch { snackbar.showSnackbar(resources.getString(R.string.transaction_deleted_receipt)) }
                    })
            }
        }
    }

    if (showEntryOptions && isMainDestination) {
        AlertDialog(
            onDismissRequest = { showEntryOptions = false },
            title = { Text(stringResource(R.string.choose_entry_mode)) },
            text = {
                Column {
                    listOf(LedgerDestination.SINGLE_ENTRY, LedgerDestination.BATCH_ENTRY)
                        .forEach { destination ->
                            TextButton(
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                                onClick = {
                                    showEntryOptions = false
                                    navController.navigate(destination.route) {
                                        launchSingleTop = true
                                    }
                                }
                            ) {
                                Text(stringResource(destination.title))
                            }
                        }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showEntryOptions = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
