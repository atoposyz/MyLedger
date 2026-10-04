package com.example.myledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.myledger.data.settings.ThemeMode
import com.example.myledger.ui.settings.SettingsViewModel
import com.example.myledger.ui.navigation.MyLedgerApp
import com.example.myledger.ui.theme.MyLedgerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val app = application as LedgerApplication
            app.repository.initialize()
            runCatching { app.backupScheduler.reconcile(app.settingsRepository) }
        }
        enableEdgeToEdge()
        setContent {
            val factory = viewModelFactory { initializer { SettingsViewModel((application as LedgerApplication).settingsRepository) } }
            val settings: SettingsViewModel = viewModel(factory = factory)
            val state by settings.state.collectAsStateWithLifecycle()
            val dark = when (state.settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            SideEffect {
                enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark })
            }
            MyLedgerTheme(darkTheme = dark) {
                MyLedgerApp()
            }
        }
    }
}

@Preview(name = "Light", showBackground = true)
@Composable
private fun MyLedgerLightPreview() {
    MyLedgerTheme(darkTheme = false, dynamicColor = false) {
        MyLedgerApp()
    }
}

@Preview(name = "Dark", showBackground = true)
@Composable
private fun MyLedgerDarkPreview() {
    MyLedgerTheme(darkTheme = true, dynamicColor = false) {
        MyLedgerApp()
    }
}
