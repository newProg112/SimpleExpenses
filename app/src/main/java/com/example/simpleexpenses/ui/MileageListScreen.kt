package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

// New: sort options for the mileage list
enum class MileageSortOption {
    DATE_DESC,   // newest first
    DATE_ASC,    // oldest first
    AMOUNT_DESC, // highest £ first
    MILES_DESC   // longest trips first
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MileageListScreen(
    vm: MileageViewModel,
    onAddClick: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val items by vm.items.collectAsState()

    val currency = remember { NumberFormat.getCurrencyInstance() }

    // --- New: simple month picker state (default: this month) ---
    data class YearMonth(val year: Int, val month: Int) {
        override fun toString(): String = if (year == -1) {
            "All"
        } else {
            "%04d-%02d".format(year, month)
        }
    }

    // Add an "All" option at the start
    val monthOptions = remember {
        val now = LocalDate.now()
        listOf(YearMonth(-1, -1)) + (0 until 12).map { off ->
            val d = now.minusMonths(off.toLong())
            YearMonth(d.year, d.monthValue)
        }
    }

    // Default to All
    var selected by remember { mutableStateOf(monthOptions[0]) }
    var monthMenuOpen by remember { mutableStateOf(false) }

    // New: sort state
    var sortOption by remember { mutableStateOf(MileageSortOption.DATE_DESC) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val sortLabel = when (sortOption) {
        MileageSortOption.DATE_DESC -> "Date ↓"
        MileageSortOption.DATE_ASC -> "Date ↑"
        MileageSortOption.AMOUNT_DESC -> "Amount £↓"
        MileageSortOption.MILES_DESC -> "Miles ↓"
    }

    // Filter mileage entries by month, or show all if "All" selected
    val filtered = remember(items, selected) {
        if (selected.year == -1) items
        else items.filter { e ->
            e.date.year == selected.year && e.date.monthValue == selected.month
        }
    }

    // Apply sorting on top of filtering
    val sorted = remember(filtered, sortOption) {
        when (sortOption) {
            MileageSortOption.DATE_DESC ->
                filtered.sortedByDescending { it.date }
            MileageSortOption.DATE_ASC ->
                filtered.sortedBy { it.date }
            MileageSortOption.AMOUNT_DESC ->
                filtered.sortedByDescending { it.amountPence }
            MileageSortOption.MILES_DESC ->
                filtered.sortedByDescending { it.distanceMeters }
        }
    }

    val monthTotalPence = remember(sorted) { sorted.sumOf { it.amountPence } }

    var toDeleteId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mileage", modifier = Modifier.padding(end = 12.dp))

                        // Month dropdown "chip"
                        androidx.compose.material3.OutlinedButton(
                            onClick = { monthMenuOpen = true }
                        ) {
                            Text(selected.toString())
                        }
                        DropdownMenu(
                            expanded = monthMenuOpen,
                            onDismissRequest = { monthMenuOpen = false }
                        ) {
                            monthOptions.forEach { ym ->
                                DropdownMenuItem(
                                    text = { Text(ym.toString()) },
                                    onClick = {
                                        selected = ym
                                        monthMenuOpen = false
                                    }
                                )
                            }
                        }

                        // Sort dropdown "chip"
                        androidx.compose.material3.OutlinedButton(
                            onClick = { sortMenuOpen = true },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text(sortLabel)
                        }
                        DropdownMenu(
                            expanded = sortMenuOpen,
                            onDismissRequest = { sortMenuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Date (newest first)") },
                                onClick = {
                                    sortOption = MileageSortOption.DATE_DESC
                                    sortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Date (oldest first)") },
                                onClick = {
                                    sortOption = MileageSortOption.DATE_ASC
                                    sortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Amount (highest £)") },
                                onClick = {
                                    sortOption = MileageSortOption.AMOUNT_DESC
                                    sortMenuOpen = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Miles (longest trips)") },
                                onClick = {
                                    sortOption = MileageSortOption.MILES_DESC
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                },
                actions = {
                    Text(
                        "Total: ${currency.format(monthTotalPence / 100.0)}",
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
        Text(
            "Debug: ${sorted.size} entries",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        if (sorted.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(pad),
                contentAlignment = Alignment.Center
            ) {
                Text("No mileage in $selected — tap + to add a trip")
            }
        } else {
            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .padding(pad)
            ) {
                items(sorted, key = { it.id }) { e ->
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
                            Text(
                                "${e.date.format(DateTimeFormatter.ISO_LOCAL_DATE)} · " +
                                        "$miles mi @ ${e.ratePencePerMile}p/mi"
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {

                                if (e.hasReceipt) {
                                    val isPdf = e.receiptUri?.lowercase()?.endsWith(".pdf") == true

                                    Icon(
                                        imageVector = if (isPdf)
                                            Icons.Outlined.PictureAsPdf
                                        else
                                            Icons.Outlined.Image,
                                        contentDescription = "Attachment",
                                        modifier = Modifier.padding(end = 6.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Text(
                                    currency.format(e.amountPence / 100.0),
                                    modifier = Modifier.padding(end = 4.dp)
                                )

                                IconButton(onClick = { toDeleteId = e.id }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.clickable { onEdit(e.id) }
                    )
                    Divider()
                }
            }

            if (toDeleteId != null) {
                AlertDialog(
                    onDismissRequest = { toDeleteId = null },
                    title = { Text("Delete mileage") },
                    text = { Text("Delete this mileage entry? This cannot be undone.") },
                    confirmButton = {
                        TextButton(onClick = {
                            val id = toDeleteId!!
                            toDeleteId = null
                            vm.delete(id)
                        }) {
                            Text("Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { toDeleteId = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}