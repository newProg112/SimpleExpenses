package com.example.simpleexpenses.ui.rows

import androidx.benchmark.traceprocessor.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.MileageEntry
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun MileageRow(
    item: MileageEntry,
    onClick: () -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance() }

    // Miles to 1 decimal place
    val miles = (item.distanceMeters / 1609.344 * 10.0).roundToInt() / 10.0

    // Friendlier date, e.g. "Mon 2 Dec"
    val dateLabel = remember(item.date) {
        item.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }

    val headline = "${item.fromLabel} → ${item.toLabel}"

    ListItem(
        headlineContent = {
            Text(
                text = headline,
                maxLines = 1,
                style = MaterialTheme.typography.bodyLarge
            )
        },
        supportingContent = {
            // Just show date + miles; don’t mention rate to avoid HMRC tier confusion
            Text(
                text = "$dateLabel · ${"%.1f".format(miles)} mi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.hasReceipt) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Has receipt",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = currency.format(item.amountPence / 100.0),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Reimbursed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
    )

    Divider()
}