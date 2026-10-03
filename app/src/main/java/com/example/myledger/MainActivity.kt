package com.example.myledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.example.myledger.ui.theme.MyLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyLedgerTheme {
                MyLedgerApp()
            }
        }
    }
}

@Composable
private fun MyLedgerApp() {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium
            )
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
