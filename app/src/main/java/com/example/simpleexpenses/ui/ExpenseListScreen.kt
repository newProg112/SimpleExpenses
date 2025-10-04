package com.example.simpleexpenses.ui

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SortOption { RECENT, OLDEST, AMOUNT_ASC, AMOUNT_DESC }

@Composable
fun FilterBar(
    filters: ExpenseFilters,
    onCategoryChange: (String?) -> Unit,
    onStatusChange: (ExpenseStatus?) -> Unit,
    onHasReceiptChange: (Boolean?) -> Unit,
    onClear: () -> Unit
) {
    var categoryText by remember(filters.category) { mutableStateOf(filters.category.orEmpty()) }
    var statusExpanded by remember { mutableStateOf(false) }
    var receiptExpanded by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = categoryText,
                onValueChange = {
                    categoryText = it
                    onCategoryChange(it.trim().ifBlank { null })
                },
                label = { Text("Category") },
                modifier = Modifier.weight(1f)
            )

            // Status dropdown
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { statusExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(filters.status?.name ?: "Status: Any")
                }
                DropdownMenu(expanded = statusExpanded, onDismissRequest = { statusExpanded = false }) {
                    DropdownMenuItem(text = { Text("Any") }, onClick = {
                        onStatusChange(null); statusExpanded = false
                    })
                    ExpenseStatus.values().forEach { s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = {
                            onStatusChange(s); statusExpanded = false
                        })
                    }
                }
            }

            // Receipt dropdown
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { receiptExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when (filters.hasReceipt) {
                            null -> "Receipt: Any"
                            true -> "Receipt: With"
                            false -> "Receipt: Without"
                        }
                    )
                }
                DropdownMenu(expanded = receiptExpanded, onDismissRequest = { receiptExpanded = false }) {
                    DropdownMenuItem(text = { Text("Any") }, onClick = { onHasReceiptChange(null); receiptExpanded = false })
                    DropdownMenuItem(text = { Text("With") }, onClick = { onHasReceiptChange(true); receiptExpanded = false })
                    DropdownMenuItem(text = { Text("Without") }, onClick = { onHasReceiptChange(false); receiptExpanded = false })
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClear) { Text("Clear filters") }
            // (Optional) Add date range later
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalLayoutApi::class
)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onExport: () -> Unit
) {
    val expenses by viewModel.expenses.collectAsState(initial = emptyList())
    val filters by viewModel.filters.collectAsState()

    // Totals (enum comparisons)
    val submittedTotal = expenses.filter { it.status == ExpenseStatus.Submitted }.sumOf { it.amount }
    val approvedTotal  = expenses.filter { it.status == ExpenseStatus.Approved  }.sumOf { it.amount }
    val paidTotal      = expenses.filter { it.status == ExpenseStatus.Paid      }.sumOf { it.amount }

    val submittedCount = expenses.count { it.status == ExpenseStatus.Submitted }
    val approvedCount  = expenses.count { it.status == ExpenseStatus.Approved  }
    val paidCount      = expenses.count { it.status == ExpenseStatus.Paid      }

    var query by rememberSaveable { mutableStateOf("") }

    val selectedStatus = filters.status
    val selectedCategory = filters.category

    var sort by rememberSaveable { mutableStateOf(SortOption.RECENT) }

    var reimbursableOnly by rememberSaveable { mutableStateOf(false) }
    var paymentFilter by rememberSaveable { mutableStateOf<String?>(null) } // "Personal" or "CompanyCard"

    var catMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var showMoreFilters by rememberSaveable { mutableStateOf(false) }
    val categoryOptions = listOf("General", "Travel", "Meals", "Supplies", "Software", "Training", "Other")

    // Apply filter
    val displayedExpenses = expenses
        // reimbursable
        .let { list -> if (!reimbursableOnly) list else list.filter { it.reimbursable } }
        // payment method
        .let { list -> if (paymentFilter == null) list else list.filter { it.paymentMethod == paymentFilter } }
        // search (title or merchant)
        .let { list ->
            if (query.isBlank()) list else list.filter {
                it.title.contains(query, ignoreCase = true) ||
                        (it.merchant?.contains(query, ignoreCase = true) == true)
            }
        }
        // sort
        .let { list ->
            when (sort) {
                SortOption.RECENT      -> list.sortedByDescending { it.timestamp }
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
            FilterBar(
                filters = filters,
                onCategoryChange = viewModel::setCategory,
                onStatusChange = viewModel::setStatus,
                onHasReceiptChange = viewModel::setHasReceipt,
                onClear = viewModel::clearFilters
            )

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

                            // Compact filter row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Category = single chip that opens a dropdown
                                FilterChip(
                                    selected = selectedCategory != null,
                                    onClick = { catMenuExpanded = true },
                                    label = { Text(selectedCategory ?: "Category") },
                                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
                                )
                                DropdownMenu(
                                    expanded = catMenuExpanded,
                                    onDismissRequest = { catMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All categories") },
                                        onClick = {
                                            viewModel.setCategory(null)
                                            catMenuExpanded = false
                                        }
                                    )
                                    categoryOptions.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c) },
                                            onClick = {
                                                viewModel.setCategory(c)
                                                catMenuExpanded = false
                                            }
                                        )
                                    }
                                }

                                val activeFilters = listOfNotNull(
                                    selectedCategory?.let { "cat" },
                                    if (reimbursableOnly) "reimb" else null,
                                    paymentFilter // "Personal" / "CompanyCard" or null
                                ).size


                                AssistChip(
                                    onClick = { showMoreFilters = !showMoreFilters },
                                    label = { Text(if (activeFilters > 0) "Filters ($activeFilters)" else "Filters") },
                                    leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) }
                                )

                                if (activeFilters > 0) {
                                    AssistChip(
                                        onClick = {
                                            viewModel.setCategory(null)
                                            reimbursableOnly = false
                                            paymentFilter = null
                                        },
                                        label = { Text("Clear") }
                                    )
                                }

                            }

                            // Extra filters collapse down when not needed
                            AnimatedVisibility(visible = showMoreFilters) {
                                val categories = listOf("General","Travel","Meals","Supplies","Software","Training","Other")

                                FlowRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = reimbursableOnly,
                                        onClick = { reimbursableOnly = !reimbursableOnly },
                                        label = { Text("Reimbursable only") }
                                    )
                                    FilterChip(
                                        selected = paymentFilter == "Personal",
                                        onClick = { paymentFilter = if (paymentFilter == "Personal") null else "Personal" },
                                        label = { Text("Personal") }
                                    )
                                    FilterChip(
                                        selected = paymentFilter == "CompanyCard",
                                        onClick = { paymentFilter = if (paymentFilter == "CompanyCard") null else "CompanyCard" },
                                        label = { Text("Company card") }
                                    )
                                }
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
                            if (selectedStatus == status) viewModel.setStatus(null) else viewModel.setStatus(status)
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
                                onClick = { viewModel.setStatus(null) },
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
                    // Defer deletion so we don't mutate state inside confirm callback
                    var pendingDelete by remember { mutableStateOf(false) }

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
                        confirmValueChange = { target ->
                            when (target) {
                                SwipeToDismissBoxValue.EndToStart,
                                SwipeToDismissBoxValue.StartToEnd -> {
                                    if (e.status == ExpenseStatus.Paid) {
                                        // Guard: confirm before deleting Paid items
                                        showConfirm = true
                                        false // don't allow the dismiss to complete
                                    } else {
                                        // Defer deletion to a LaunchedEffect
                                        pendingDelete = true
                                        true  // allow the dismiss animation
                                    }
                                }
                                else -> false
                            }
                        }
                    )

                    // Perform the delete AFTER confirmValueChange, safely
                    LaunchedEffect(pendingDelete) {
                        if (pendingDelete) {
                            pendingDelete = false
                            onDelete()
                        }
                    }

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
                            overlineContent = {
                                val parts = buildList {
                                    if (!e.merchant.isNullOrBlank()) add(e.merchant!!)
                                    add(e.category)
                                    if (!e.reimbursable) add("Not reimbursable")
                                    add(if (e.paymentMethod == "CompanyCard") "Company card" else "Personal")
                                }
                                Text(parts.joinToString(" • "))
                            },
                            headlineContent = { Text(e.title) },
                            supportingContent = { Text("£${"%.2f".format(e.amount)}") },
                            trailingContent = {
                                // NEW: show a small badge for receipt type
                                val context = LocalContext.current
                                val receiptUri = e.receiptUri
                                val isPdf = remember(receiptUri) {
                                    receiptUri?.let {
                                        // MIME check, with safe fallback to .pdf extension
                                        val t = runCatching { context.contentResolver.getType(Uri.parse(it)) }.getOrNull()
                                        t == "application/pdf" || it.endsWith(".pdf", ignoreCase = true)
                                    } ?: false
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!receiptUri.isNullOrBlank()) {
                                        Icon(
                                            imageVector = if (isPdf) Icons.Filled.Description else Icons.Filled.Image,
                                            contentDescription = if (isPdf) "PDF receipt" else "Image receipt",
                                            tint = if (isPdf) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

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