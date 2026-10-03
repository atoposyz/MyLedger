package com.example.myledger.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.myledger.R

@Composable
fun SettingsScreen(onOpenActivities: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onOpenActivities, modifier = Modifier.fillMaxWidth().testTag("settings_activities")) {
            Text(stringResource(R.string.activities))
        }
        Text(stringResource(R.string.settings_empty_message), style = MaterialTheme.typography.bodyMedium)
    }
}
