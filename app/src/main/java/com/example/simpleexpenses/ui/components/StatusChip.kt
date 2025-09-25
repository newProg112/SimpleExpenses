package com.example.simpleexpenses.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.ExpenseStatus

@Composable
fun StatusChip(status: ExpenseStatus, modifier: Modifier = Modifier) {
    val bg = when (status) {
        ExpenseStatus.Submitted -> MaterialTheme.colorScheme.surfaceVariant
        ExpenseStatus.Approved  -> MaterialTheme.colorScheme.secondaryContainer
        ExpenseStatus.Paid      -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val fg = when (status) {
        ExpenseStatus.Submitted -> MaterialTheme.colorScheme.onSurfaceVariant
        ExpenseStatus.Approved  -> MaterialTheme.colorScheme.onSecondaryContainer
        ExpenseStatus.Paid      -> MaterialTheme.colorScheme.onTertiaryContainer
    }

    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = status.name,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}