package com.example.simpleexpenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onExport: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState(initial = emptyList())
    val total by viewModel.total.collectAsState(initial = 0.0)
    val currency = remember { NumberFormat.getCurrencyInstance() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Simple Expenses") },
                actions = {
                    TextButton(onClick = onExport) { Text("Export") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Text("+") }
        }
    ) { pad ->
        if (expenses.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No expenses yet. Tap + to add.")
            }
        } else {
            Column(Modifier.padding(pad)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total")
                    Text(currency.format(total))
                }
                Divider()
                LazyColumn {
                    items(expenses) { e: Expense ->
                        ListItem(
                            headlineContent = { Text(e.title) },
                            supportingContent = { Text(currency.format(e.amount)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEdit(e.id) }
                                .padding(horizontal = 8.dp)
                        )
                        Divider()
                    }
                }
            }
        }
    }
}