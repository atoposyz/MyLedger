package com.example.myledger.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

/** Keep category names readable when an amount or the user's font size is large. */
@Composable
internal fun RecordAmountRow(label: String, amount: String, amountColor: Color) {
    val style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (measurer.measure(amount, style).size.width > constraints.maxWidth / 2) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = style)
                AmountText(amount, style = style, color = amountColor)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(label, style = style, modifier = Modifier.weight(1f))
                Text(amount, style = style, color = amountColor)
            }
        }
    }
}
