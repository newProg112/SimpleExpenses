package com.example.simpleexpenses.ui.rows

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    val miles = (item.distanceMeters / 1609.344 * 10.0).roundToInt() / 10.0
    val date = item.date.format(DateTimeFormatter.ISO_LOCAL_DATE)

    ListItem(
        headlineContent = {
            Text("${item.fromLabel} → ${item.toLabel}", maxLines = 1)
        },
        supportingContent = {
            Text("$date · $miles mi @ ${item.ratePencePerMile}p/mi")
        },
        trailingContent = {
            Text(currency.format(item.amountPence / 100.0))
        },
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp)
    )
    Divider()
}