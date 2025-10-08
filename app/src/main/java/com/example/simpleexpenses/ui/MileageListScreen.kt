package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MileageListScreen(
    vm: MileageViewModel,
    onAddClick: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val items by vm.items.collectAsState()
    val today = LocalDate.now()
    val monthTotalPence by vm.totalPenceInMonth(today.year, today.monthValue)
        .collectAsState(initial = 0)
    val currency = remember { NumberFormat.getCurrencyInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mileage") },
                actions = {
                    Text(
                        "This month: ${currency.format(monthTotalPence / 100.0)}",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddClick) { Text("+") }
        }
    ) { pad ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("No mileage yet — tap + to add a trip")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                items(items, key = { it.id }) { e ->
                    val miles = (e.distanceMeters / 1609.344 * 10.0).roundToInt() / 10.0
                    ListItem(
                        headlineContent = {
                            Text(
                                text = "${e.fromLabel} → ${e.toLabel}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text("${e.date.format(DateTimeFormatter.ISO_LOCAL_DATE)} · $miles mi @ ${e.ratePencePerMile}p/mi")
                        },
                        trailingContent = {
                            Text(currency.format(e.amountPence / 100.0))
                        },
                        modifier = Modifier.clickable { onEdit(e.id) }
                    )

                    Divider()
                }
            }
        }
    }
}