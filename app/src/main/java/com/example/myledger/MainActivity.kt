package com.example.myledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.myledger.ui.navigation.MyLedgerApp
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
