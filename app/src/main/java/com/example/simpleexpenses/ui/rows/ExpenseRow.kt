package com.example.simpleexpenses.ui.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.ui.components.StatusChip
import java.text.NumberFormat

@Composable
fun ExpenseRow(
    item: Expense,
    onClick: () -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance() }
    val context = LocalContext.current
    val receiptUri = item.receiptUri

    ListItem(
        headlineContent = { Text(item.title) },
        supportingContent = {
            Text(
                "£${"%.2f".format(item.amount)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        trailingContent = {
            StatusChip(item.status)
        },
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
    )
    Divider()
}