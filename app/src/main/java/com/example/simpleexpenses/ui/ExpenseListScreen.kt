package com.example.simpleexpenses.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onExport: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState(initial = emptyList())

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
        LazyColumn(Modifier.padding(pad)) {
            items(expenses) { e: Expense ->
                ListItem(
                    headlineContent = { Text(e.title) },
                    supportingContent = { Text("£${"%.2f".format(e.amount)}") },
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