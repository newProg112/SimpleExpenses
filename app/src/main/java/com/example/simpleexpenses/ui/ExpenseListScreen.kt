package com.example.simpleexpenses.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SortOption { RECENT, OLDEST, AMOUNT_ASC, AMOUNT_DESC }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onExport: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState(initial = emptyList())

    // Totals (enum comparisons)
    val submittedTotal = expenses.filter { it.status == ExpenseStatus.Submitted }.sumOf { it.amount }
    val approvedTotal  = expenses.filter { it.status == ExpenseStatus.Approved  }.sumOf { it.amount }
    val paidTotal      = expenses.filter { it.status == ExpenseStatus.Paid      }.sumOf { it.amount }

    val submittedCount = expenses.count { it.status == ExpenseStatus.Submitted }
    val approvedCount  = expenses.count { it.status == ExpenseStatus.Approved  }
    val paidCount      = expenses.count { it.status == ExpenseStatus.Paid      }


    // Selected filter name (persisted across rotation/process recreation)
    var selectedStatusName by rememberSaveable { mutableStateOf<String?>(null) }

    var query by rememberSaveable { mutableStateOf("") }

    // Map to enum (safe)
    val selectedStatus = selectedStatusName?.let { name ->
        runCatching { ExpenseStatus.valueOf(name) }.getOrNull()
    }

    var sort by rememberSaveable { mutableStateOf(SortOption.RECENT) }

    // Apply filter
    val displayedExpenses = expenses
        // status filter
        .let { list -> if (selectedStatus == null) list else list.filter { it.status == selectedStatus } }
        // search filter
        .let { list -> if (query.isBlank()) list else list.filter { it.title.contains(query, ignoreCase = true) } }
        // sort
        .let { list ->
            when (sort) {
                SortOption.RECENT      -> list.sortedByDescending { it.timestamp } // newest first
                SortOption.OLDEST      -> list.sortedBy { it.timestamp }
                SortOption.AMOUNT_ASC  -> list.sortedBy { it.amount }
                SortOption.AMOUNT_DESC -> list.sortedByDescending { it.amount }
            }
        }

    val visibleCount = displayedExpenses.size
    val visibleTotal = displayedExpenses.sumOf { it.amount }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val haptic = LocalHapticFeedback.current

    var menuForId by remember { mutableStateOf<Long?>(null) }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var refreshing by remember { mutableStateOf(false) }
    val ptrState = rememberPullToRefreshState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Simple Expenses") },
                actions = { TextButton(onClick = onExport) { Text("Export") } },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+") } },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val label = if (selectedStatus != null || query.isNotBlank()) "Filtered total" else "Total"
                    Text(
                        text = "$label",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "£${"%.2f".format(visibleTotal)} • $visibleCount ${if (visibleCount == 1) "item" else "items"}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    ) { pad ->
        PullToRefreshBox(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            state = ptrState,
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch {
                    // TODO: call viewModel.refresh() here when you have one
                    delay(600)
                    refreshing = false
                }
            },
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = ptrState,
                    isRefreshing = refreshing,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                // Summary at the top
                stickyHeader {
                    // Surface avoids transparency when stuck
                    Surface(tonalElevation = 2.dp) {
                        Column {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Search,
                                        contentDescription = null
                                    )
                                },
                                trailingIcon = {
                                    if (query.isNotBlank()) {
                                        IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                placeholder = { Text("Search expenses") },
                                shape = RoundedCornerShape(12.dp)
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = sort == SortOption.RECENT,
                                    onClick = { sort = SortOption.RECENT },
                                    label = { Text("Recent") }
                                )
                                FilterChip(
                                    selected = sort == SortOption.OLDEST,
                                    onClick = { sort = SortOption.OLDEST },
                                    label = { Text("Oldest") }
                                )
                                FilterChip(
                                    selected = sort == SortOption.AMOUNT_ASC,
                                    onClick = { sort = SortOption.AMOUNT_ASC },
                                    label = { Text("Amount ↑") }
                                )
                                FilterChip(
                                    selected = sort == SortOption.AMOUNT_DESC,
                                    onClick = { sort = SortOption.AMOUNT_DESC },
                                    label = { Text("Amount ↓") }
                                )
                            }
                        }
                    }
                }

                item {
                    SummarySection(
                        submittedTotal = submittedTotal,
                        approvedTotal = approvedTotal,
                        paidTotal = paidTotal,
                        submittedCount = submittedCount,
                        approvedCount = approvedCount,
                        paidCount = paidCount,
                        selected = selectedStatus,
                        onCardClick = { status ->
                            selectedStatusName = if (selectedStatus == status) null else status.name
                        }
                    )
                }

                if (query.isNotBlank()) {
                    item {
                        Text(
                            text = "Showing ${displayedExpenses.size} result${if (displayedExpenses.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                        )
                    }
                }

                // Show a clear-filter chip when a status is selected
                if (selectedStatus != null) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filtered: " + selectedStatus!!.name.lowercase()
                                    .replaceFirstChar { it.titlecase() },
                                style = MaterialTheme.typography.bodyMedium
                            )
                            AssistChip(
                                onClick = { selectedStatusName = null },
                                label = { Text("Clear filter") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                        Divider()
                    }
                }

                items(
                    items = displayedExpenses,
                    key = { it.id }
                ) { e: Expense ->

                    // Show confirm for Paid only
                    var showConfirm by remember { mutableStateOf(false) }

                    // One place to perform delete + snackbar + undo + haptics
                    val onDelete: () -> Unit = {
                        scope.launch {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.delete(e)
                            val res = snackbarHostState.showSnackbar(
                                message = "Deleted '${e.title}'",
                                actionLabel = "Undo",
                                withDismissAction = true,
                                duration = SnackbarDuration.Short
                            )
                            if (res == SnackbarResult.ActionPerformed) {
                                viewModel.add(e)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    }

                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart ||
                                value == SwipeToDismissBoxValue.StartToEnd
                            ) {
                                if (e.status == ExpenseStatus.Paid) {
                                    // Guard: confirm before deleting Paid items
                                    showConfirm = true
                                    false // cancel swipe completion; we’ll handle via the dialog
                                } else {
                                    onDelete()
                                    true
                                }
                            } else {
                                false
                            }
                        }
                    )

                    // The swipe container (same background/content as before)
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    ) {
                        ListItem(
                            headlineContent = { Text(e.title) },
                            supportingContent = { Text("£${"%.2f".format(e.amount)}") },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // keep your existing chip
                                    com.example.simpleexpenses.ui.components.StatusChip(e.status)

                                    IconButton(onClick = { menuForId = e.id }) {
                                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                                    }

                                    DropdownMenu(
                                        expanded = menuForId == e.id,
                                        onDismissRequest = { menuForId = null }
                                    ) {
                                        fun choose(newStatus: ExpenseStatus, label: String) {
                                            scope.launch {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.update(e.copy(status = newStatus))
                                                snackbarHostState.showSnackbar("Marked as $label")
                                            }
                                            menuForId = null
                                        }

                                        DropdownMenuItem(
                                            text = { Text("Mark as Submitted") },
                                            onClick = {
                                                choose(
                                                    ExpenseStatus.Submitted,
                                                    "Submitted"
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark as Approved") },
                                            onClick = { choose(ExpenseStatus.Approved, "Approved") }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Mark as Paid") },
                                            onClick = { choose(ExpenseStatus.Paid, "Paid") }
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .clickable { onEdit(e.id) }
                                .padding(horizontal = 8.dp)
                        )
                    }

                    Divider()

                    // Confirmation dialog for Paid
                    if (showConfirm) {
                        AlertDialog(
                            onDismissRequest = { showConfirm = false },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            title = { Text("Delete paid expense?") },
                            text = { Text("This item is marked as Paid. Are you sure you want to delete it?") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showConfirm = false
                                    onDelete()
                                }) { Text("Delete") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                            }
                        )
                    }
                }

            }
        }
    }
}