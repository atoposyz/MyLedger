package com.example.myledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA4C9E7), onPrimary = Color(0xFF15344D),
    primaryContainer = Color(0xFF263F55), onPrimaryContainer = Color(0xFFDFEBF6),
    secondary = Color(0xFFB7C7D5), secondaryContainer = Color(0xFF344451), onSecondaryContainer = Color(0xFFDBE7F0),
    tertiary = Color(0xFFB7C7D5),
    background = Color(0xFF101419), onBackground = Color(0xFFDEE5ED),
    surface = Color(0xFF101419), onSurface = Color(0xFFDEE5ED),
    surfaceContainerLow = Color(0xFF191E25), surfaceContainer = Color(0xFF1D242C), surfaceContainerHigh = Color(0xFF27303A),
    surfaceVariant = Color(0xFF34404D), onSurfaceVariant = Color(0xFFBAC6D3),
    outline = Color(0xFF8C99A7), outlineVariant = Color(0xFF3D4855)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3F617E), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE6F1), onPrimaryContainer = Color(0xFF203C54),
    secondary = Color(0xFF536575), secondaryContainer = Color(0xFFE1E8EF), onSecondaryContainer = Color(0xFF293E4E),
    tertiary = Color(0xFF536575),
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF1B232D),
    surface = Color(0xFFF6F7F9), onSurface = Color(0xFF1B232D),
    surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFEDF1F5), surfaceContainerHigh = Color(0xFFE5EBF1),
    surfaceVariant = Color(0xFFE1E8EF), onSurfaceVariant = Color(0xFF4C5B69),
    outline = Color(0xFF738292), outlineVariant = Color(0xFFCAD3DE)
)

@Composable
fun MyLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
