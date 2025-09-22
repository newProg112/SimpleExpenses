package com.example.simpleexpenses.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    viewModel: ExpenseViewModel,
    expenseId: Long? = null,
    onDone: () -> Unit
) {
    // Simple create-only form for now
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text(if (expenseId != null) "Edit Expense" else "New Expense") }) }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = amountText, onValueChange = { amountText = it },
                label = { Text("Amount") }, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("Enter a number, e.g. 4.50") }
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amt > 0.0) {
                        viewModel.add(Expense(title = title, amount = amt))
                        onDone()
                    }
                },
                enabled = title.isNotBlank() && amountText.toDoubleOrNull()?.let { it > 0.0 } == true
            ) { Text("Save") }
        }
    }
}
