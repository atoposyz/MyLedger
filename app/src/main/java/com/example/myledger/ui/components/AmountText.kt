package com.example.myledger.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** Display money as one number, including its sign and fractional digits. */
@Composable
internal fun AmountText(text: String, modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium, color: Color = MaterialTheme.colorScheme.onSurface) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val width = measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        val ratio = if (width > constraints.maxWidth) (constraints.maxWidth - 8).coerceAtLeast(0).toFloat() / width else 1f
        val adjusted = style.copy(fontSize = maxOf(12f, style.fontSize.value * ratio).sp)
        Text(text, style = adjusted, color = color, maxLines = 1, softWrap = false,
            modifier = Modifier.horizontalScroll(rememberScrollState()))
    }
}
