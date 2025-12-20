package com.example.simpleexpenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagerScreen(
    viewModel: CategoryViewModel,
    onBack: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()

    var showAdd by remember { mutableStateOf(false) }
    var addText by remember { mutableStateOf("") }

    var menuFor by remember { mutableStateOf<Long?>(null) }

    var renaming by remember { mutableStateOf<com.example.simpleexpenses.data.ExpenseCategory?>(null) }
    var renameText by remember { mutableStateOf("") }

    var deleting by remember { mutableStateOf<com.example.simpleexpenses.data.ExpenseCategory?>(null) }

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    val msg by viewModel.uiMessage.collectAsState()

    LaunchedEffect(msg) {
        msg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Manage categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = { showAdd = true }) {
                Text("Add category")
            }

            Divider()

            categories.forEachIndexed { index, category ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )

                    // Up / Down ordering
                    IconButton(
                        onClick = { viewModel.moveUp(category) },
                        enabled = index > 0
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Move up"
                        )
                    }

                    IconButton(
                        onClick = { viewModel.moveDown(category) },
                        enabled = index < categories.lastIndex
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Move down"
                        )
                    }

                    // Toggle active/inactive (your current behavior)
                    TextButton(
                        onClick = { viewModel.setActive(category, !category.isActive) }
                    ) {
                        Text(if (category.isActive) "Hide" else "Show")
                    }

                    IconButton(onClick = { menuFor = category.id }) {
                        Icon(imageVector = Icons.Filled.MoreVert, contentDescription = "More")
                    }

                    DropdownMenu(
                        expanded = menuFor == category.id,
                        onDismissRequest = { menuFor = null }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuFor = null
                                renaming = category
                                renameText = category.name
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuFor = null
                                deleting = category
                            }
                        )
                    }
                }

                Divider()
            }
        }

        // Add dialog
        if (showAdd) {
            AlertDialog(
                onDismissRequest = { showAdd = false },
                title = { Text("Add category") },
                text = {
                    OutlinedTextField(
                        value = addText,
                        onValueChange = { addText = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.addCategory(addText)
                            addText = ""
                            showAdd = false
                        },
                        enabled = addText.trim().isNotEmpty()
                    ) { Text("Add") }
                },
                dismissButton = {
                    TextButton(onClick = { showAdd = false }) { Text("Cancel") }
                }
            )
        }

        // Rename dialog
        val toRename = renaming
        if (toRename != null) {
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("Rename category") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        label = { Text("Name") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.rename(toRename, renameText)
                            renaming = null
                        },
                        enabled = renameText.trim().isNotEmpty()
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { renaming = null }) { Text("Cancel") }
                }
            )
        }

        // Delete confirm
        val toDelete = deleting
        if (toDelete != null) {
            AlertDialog(
                onDismissRequest = { deleting = null },
                title = { Text("Delete category") },
                text = { Text("Delete \"${toDelete.name}\"? This cannot be undone.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.delete(toDelete)
                            deleting = null
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { deleting = null }) { Text("Cancel") }
                }
            )
        }
    }
}