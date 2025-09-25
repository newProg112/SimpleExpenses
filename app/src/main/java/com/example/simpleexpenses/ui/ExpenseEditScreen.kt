package com.example.simpleexpenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEditScreen(
    viewModel: ExpenseViewModel,
    expenseId: Long? = null,
    onDone: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(ExpenseStatus.Submitted) }
    val scope = rememberCoroutineScope()

    // Prefill if editing
    LaunchedEffect(expenseId) {
        if (expenseId != null) {
            viewModel.get(expenseId)?.let { e ->
                title = e.title
                amountText = e.amount.toString()
                status = e.status
            }
        }
    }

    val saveEnabled = title.isNotBlank() && amountText.toDoubleOrNull()?.let { it > 0.0 } == true

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
            Spacer(Modifier.height(12.dp))

            // ← NEW: show the status picker
            StatusPicker(value = status, onValueChange = { status = it })
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        scope.launch {
                            val amt = amountText.toDoubleOrNull() ?: return@launch
                            if (expenseId == null) {
                                // create with status
                                viewModel.add(
                                    Expense(title = title, amount = amt, status = status)
                                )
                            } else {
                                // update (preserve id) with status
                                viewModel.update(
                                    Expense(id = expenseId, title = title, amount = amt, status = status)
                                )
                            }
                            onDone()
                        }
                    },
                    enabled = saveEnabled
                ) { Text("Save") }

                if (expenseId != null) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val amt = amountText.toDoubleOrNull() ?: 0.0
                                viewModel.delete(
                                    Expense(id = expenseId, title = title.ifBlank { "-" }, amount = amt, status = status)
                                )
                                onDone()
                            }
                        }
                    ) { Text("Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPicker(
    value: ExpenseStatus,
    onValueChange: (ExpenseStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = ExpenseStatus.values()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.name,
            onValueChange = {},
            label = { Text("Status") },
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { st ->
                DropdownMenuItem(
                    text = { Text(st.name) },
                    onClick = {
                        onValueChange(st)
                        expanded = false
                    }
                )
            }
        }
    }
}