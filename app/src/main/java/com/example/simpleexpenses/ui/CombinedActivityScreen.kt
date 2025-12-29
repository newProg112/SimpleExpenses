package com.example.simpleexpenses.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.MileageEntry
import com.example.simpleexpenses.notify.ReminderScheduler
import com.example.simpleexpenses.ui.rows.ExpenseRow
import com.example.simpleexpenses.ui.rows.MileageRow
import com.example.simpleexpenses.widget.SimpleExpensesWidgetProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed class CombinedItem {
    data class ExpenseItem(val e: Expense) : CombinedItem()
    data class MileageItem(val m: MileageEntry) : CombinedItem()
}

enum class CombinedFilter { ALL, EXPENSES, MILEAGE, MISSING_RECEIPTS }

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CombinedActivityScreen(
    expenseVM: ExpenseViewModel,
    mileageVM: MileageViewModel,
    onExpenseClick: (Long) -> Unit,
    onMileageClick: (Long) -> Unit,
    onAddExpense: () -> Unit,
    onAddMileage: () -> Unit,
    onOpenExport: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartExpenseFromCamera: (Uri) -> Unit,
    onStartMileageFromCamera: (Uri) -> Unit,
    quickAddCamera: Boolean = false,
    quickAddCameraTrigger: Long = 0L
) {
    val expenses by expenseVM.expenses.collectAsState(initial = emptyList())
    val mileage by mileageVM.items.collectAsState(initial = emptyList())

    val filterSaver: Saver<CombinedFilter, String> = Saver(
        save = { it.name },
        restore = { CombinedFilter.valueOf(it) }
    )

    var filter by rememberSaveable(stateSaver = filterSaver) { mutableStateOf(CombinedFilter.ALL) }

    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    val items = remember(expenses, mileage, filter) {
        val list: List<CombinedItem> = when (filter) {
            CombinedFilter.ALL ->
                expenses.map { CombinedItem.ExpenseItem(it) } + mileage.map {
                    CombinedItem.MileageItem(
                        it
                    )
                }

            CombinedFilter.EXPENSES ->
                expenses.map { CombinedItem.ExpenseItem(it) }

            CombinedFilter.MILEAGE ->
                mileage.map { CombinedItem.MileageItem(it) }

            CombinedFilter.MISSING_RECEIPTS ->
                expenses
                    .filter { !it.hasReceipt }
                    .map { CombinedItem.ExpenseItem(it) }
        }

        list.sortedByDescending { item ->
            when (item) {
                is CombinedItem.ExpenseItem ->
                    Instant.ofEpochMilli(item.e.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()

                is CombinedItem.MileageItem ->
                    item.m.date.atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
            }
        }
    }

    val context = LocalContext.current
    val workManager = remember { WorkManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var menuOpen by remember { mutableStateOf(false) }

    // Android 13+ notification permission launcher
    val requestNotifPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Optional: you can show a snackbar if you want
            // scope.launch { snackbarHostState.showSnackbar("Notifications disabled") }
        }
    }

    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }

    val clearPendingPhoto: (Boolean) -> Unit = { delete ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (delete && uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }

    var pendingDeleteExpense by remember { mutableStateOf<Expense?>(null) }
    var pendingDeleteMileage by remember { mutableStateOf<MileageEntry?>(null) }

    val cameraLauncherForDraft = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            // keep pendingCaptureUri so the dialog buttons can use it
            showAddDialog = true
        } else {
            // Clean up empty entry if the user cancelled
            pendingCaptureUri?.let { context.contentResolver.delete(it, null, null) }
            pendingCaptureUri = null
        }
    }

    var quickAddConsumed by rememberSaveable { mutableStateOf(false) }

    var lastQuickTrigger by rememberSaveable { mutableStateOf(0L) }

    LaunchedEffect(quickAddCameraTrigger) {
        if (quickAddCameraTrigger != 0L && quickAddCameraTrigger != lastQuickTrigger) {
            lastQuickTrigger = quickAddCameraTrigger

            val uri = createImageUri(context)
            pendingCaptureUri = uri
            if (uri != null) cameraLauncherForDraft.launch(uri)
        }
    }

    // Group the combined items by day, newest day first
    val groupedByDate = remember(items) {
        items.groupBy { item ->
            when (item) {
                is CombinedItem.ExpenseItem ->
                    Instant.ofEpochMilli(item.e.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()

                is CombinedItem.MileageItem ->
                    item.m.date
            }
        }.toSortedMap(compareByDescending { it })
    }

    LaunchedEffect(filter) { listState.scrollToItem(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                actions = {
                    IconButton(onClick = onOpenExport) {
                        Icon(
                            imageVector = Icons.Outlined.IosShare,
                            contentDescription = "Export"
                        )
                    }

                    // 3-dot notifications menu
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Notifications & reminders"
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Enable notifications") },
                            onClick = {
                                menuOpen = false
                                if (Build.VERSION.SDK_INT >= 33) {
                                    requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send test notification") },
                            onClick = {
                                menuOpen = false
                                ReminderScheduler.testNow(context)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Schedule daily @ 19:00") },
                            onClick = {
                                menuOpen = false
                                ReminderScheduler.scheduleDaily(
                                    workManager,
                                    hour = 19,
                                    minute = 0,
                                    title = "Daily reminder",
                                    message = "Remember to log your expenses/mileage."
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Cancel reminders") },
                            onClick = {
                                menuOpen = false
                                ReminderScheduler.cancelAll(workManager)
                            }
                        )
                    }

                    // Settings
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 🔝 Top: "+" FAB – opens the claim type chooser
                FloatingActionButton(
                    onClick = {
                        // Use whatever you currently use to show the dialog:
                        // e.g. showNewClaimDialog = true or showAddDialog = true
                        showAddDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "New claim"
                    )
                }

                // 🔻 Bottom: larger camera FAB for quick in-field capture
                FloatingActionButton(
                    onClick = {
                        val uri = createImageUri(context)
                        pendingCaptureUri = uri
                        if (uri != null) {
                            cameraLauncherForDraft.launch(uri)
                        }
                    },
                    modifier = Modifier.size(72.dp) // bigger touch target than default
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "New expense from photo"
                    )
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(pad)
                .fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Filter row (sticky)
            stickyHeader {
                Surface {
                    Column {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = filter == CombinedFilter.ALL,
                                onClick = { filter = CombinedFilter.ALL },
                                label = { Text("All") }
                            )
                            FilterChip(
                                selected = filter == CombinedFilter.EXPENSES,
                                onClick = { filter = CombinedFilter.EXPENSES },
                                label = { Text("Expenses") }
                            )
                            FilterChip(
                                selected = filter == CombinedFilter.MILEAGE,
                                onClick = { filter = CombinedFilter.MILEAGE },
                                label = { Text("Mileage") }
                            )
                            FilterChip(
                                selected = filter == CombinedFilter.MISSING_RECEIPTS,
                                onClick = { filter = CombinedFilter.MISSING_RECEIPTS },
                                label = { Text("Missing receipts") }
                            )
                        }
                        Divider()
                    }
                }
            }

            // --- SUMMARY CALCULATIONS (normal item, not inside stickyHeader) ---
            item(key = "summary") {
                val now = remember { java.time.LocalDate.now() }
                val monthStart = remember { now.withDayOfMonth(1) }

                val expensesThisMonth = expenses.filter { e ->
                    val d = java.time.Instant.ofEpochMilli(e.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    d >= monthStart
                }

                val mileageThisMonth = mileage.filter { m -> m.date >= monthStart }

                val expenseTotalMonth = expensesThisMonth.sumOf { it.amount }
                val mileageTotalMonth = mileageThisMonth.sumOf { it.amountPence } / 100.0
                val combinedTotal = expenseTotalMonth + mileageTotalMonth
                val missingReceipts = expenses.count { !it.hasReceipt }

                val currency = remember { java.text.NumberFormat.getCurrencyInstance() }
                val appContext = LocalContext.current.applicationContext

                LaunchedEffect(combinedTotal, expenseTotalMonth, mileageTotalMonth, missingReceipts) {
                    val prefs = appContext.getSharedPreferences("simple_expenses_widget", Context.MODE_PRIVATE)

                    prefs.edit()
                        .putString("widget_combined_total", currency.format(combinedTotal))
                        .putString("widget_expense_total", currency.format(expenseTotalMonth))
                        .putString("widget_mileage_total", currency.format(mileageTotalMonth))
                        .putInt("widget_missing_receipts", missingReceipts)
                        .apply()

                    SimpleExpensesWidgetProvider.forceUpdate(appContext)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("This Month", style = MaterialTheme.typography.titleMedium)
                            Text(currency.format(combinedTotal), style = MaterialTheme.typography.headlineLarge)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Expenses", style = MaterialTheme.typography.titleSmall)
                                Text(currency.format(expenseTotalMonth), style = MaterialTheme.typography.headlineSmall)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Mileage", style = MaterialTheme.typography.titleSmall)
                                Text(currency.format(mileageTotalMonth), style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }

                    if (missingReceipts > 0) {
                        val isActive = filter == CombinedFilter.MISSING_RECEIPTS
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { filter = CombinedFilter.MISSING_RECEIPTS },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isActive)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Missing receipts", style = MaterialTheme.typography.titleSmall)
                                Text("$missingReceipts expense(s)", style = MaterialTheme.typography.bodyLarge)
                                if (!isActive) {
                                    Text(
                                        "Tap to show only expenses missing receipts",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Divider() }

            if (items.isEmpty()) {
                item(key = "empty_state") {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = when (filter) {
                                CombinedFilter.MISSING_RECEIPTS -> "Nice! No expenses are missing receipts."
                                CombinedFilter.EXPENSES -> "No expenses logged yet."
                                CombinedFilter.MILEAGE -> "No mileage trips logged yet."
                                CombinedFilter.ALL -> "No activity yet."
                            },
                            style = MaterialTheme.typography.titleMedium
                        )

                        Spacer(modifier = Modifier.padding(4.dp))

                        Text(
                            text = when (filter) {
                                CombinedFilter.MISSING_RECEIPTS -> "Attach receipts from the editor screen to clear this."
                                CombinedFilter.EXPENSES -> "Tap + then Expense to add your first expense."
                                CombinedFilter.MILEAGE -> "Tap + then Mileage to add your first trip."
                                CombinedFilter.ALL -> "Tap + to start logging."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                groupedByDate.forEach { (date, dayItems) ->

                    val dayTotal = dayItems.sumOf { item ->
                        when (item) {
                            is CombinedItem.ExpenseItem -> item.e.amount
                            is CombinedItem.MileageItem -> item.m.amountPence / 100.0
                        }
                    }

                    item(key = "header_$date") {
                        ActivityDateHeader(date = date, totalAmount = dayTotal)
                    }

                    items(
                        items = dayItems,
                        key = { dayItem ->
                            when (dayItem) {
                                is CombinedItem.ExpenseItem -> "expense_${dayItem.e.id}"
                                is CombinedItem.MileageItem -> "mileage_${dayItem.m.id}"
                            }
                        }
                    ) { dayItem ->
                        when (dayItem) {
                            is CombinedItem.ExpenseItem -> {
                                val e = dayItem.e
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { fullWidth -> fullWidth * 0.55f },
                                    confirmValueChange = { value ->
                                        when (value) {
                                            SwipeToDismissBoxValue.StartToEnd -> { onExpenseClick(e.id); false }
                                            SwipeToDismissBoxValue.EndToStart -> { pendingDeleteExpense = e; false }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromEndToStart = true,
                                    enableDismissFromStartToEnd = true,
                                    backgroundContent = {
                                        SwipeActionBackground(dismissState.dismissDirection)
                                    }
                                ) {
                                    ExpenseActivityCard(e = e, onClick = { onExpenseClick(e.id) })
                                }
                            }

                            is CombinedItem.MileageItem -> {
                                val m = dayItem.m
                                val dismissState = rememberSwipeToDismissBoxState(
                                    positionalThreshold = { fullWidth -> fullWidth * 0.55f },
                                    confirmValueChange = { value ->
                                        when (value) {
                                            SwipeToDismissBoxValue.StartToEnd -> { onMileageClick(m.id); false }
                                            SwipeToDismissBoxValue.EndToStart -> { pendingDeleteMileage = m; false }
                                            SwipeToDismissBoxValue.Settled -> false
                                        }
                                    }
                                )
                                SwipeToDismissBox(
                                    state = dismissState,
                                    enableDismissFromEndToStart = true,
                                    enableDismissFromStartToEnd = true,
                                    backgroundContent = {
                                        SwipeActionBackground(dismissState.dismissDirection)
                                    }
                                ) {
                                    MileageActivityCard(m = m, onClick = { onMileageClick(m.id) })
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAddDialog = false
                    clearPendingPhoto(true)
                },
                title = { Text("New claim") },
                text = { Text("What would you like to add?") },
                confirmButton = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showAddDialog = false
                                pendingCaptureUri?.let {
                                    onStartExpenseFromCamera(it)
                                    clearPendingPhoto(false)
                                } ?: onAddExpense()
                            }
                        ) {
                            Text("Expense")
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showAddDialog = false
                                pendingCaptureUri?.let {
                                    onStartMileageFromCamera(it)
                                    clearPendingPhoto(false)
                                } ?: onAddMileage()
                            }
                        ) {
                            Text("Mileage")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAddDialog = false
                        clearPendingPhoto(true)
                    }) { Text("Cancel") }
                }
            )
        }

        // Delete confirm: Expense
        if (pendingDeleteExpense != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteExpense = null },
                title = { Text("Delete expense") },
                text = { Text("Are you sure? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        val toDelete = pendingDeleteExpense!!
                        pendingDeleteExpense = null
                        expenseVM.delete(toDelete)
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteExpense = null }) { Text("Cancel") }
                }
            )
        }

        // Delete confirm: Mileage
        if (pendingDeleteMileage != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteMileage = null },
                title = { Text("Delete mileage") },
                text = { Text("Are you sure? This cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        val toDelete = pendingDeleteMileage!!
                        pendingDeleteMileage = null
                        mileageVM.delete(toDelete.id)
                    }) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteMileage = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun ExpenseActivityCard(
    e: Expense,
    onClick: () -> Unit
) {
    val attachmentsCount =
        e.attachmentUris.size.takeIf { it > 0 }
            ?: if (e.hasReceipt && e.receiptUri != null) 1 else 0

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Expense",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )

                Spacer(Modifier.weight(1f))

                if (!e.hasReceipt) {
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = { Text("Missing receipt") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    )
                }

                if (attachmentsCount > 0) {
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = { Text("📎 $attachmentsCount") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Divider()

            ExpenseRow(
                item = e,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun MileageActivityCard(
    m: MileageEntry,
    onClick: () -> Unit
) {
    val hasAttachments = m.hasReceipt || !m.receiptUri.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Mileage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )

                Spacer(Modifier.weight(1f))

                if (hasAttachments) {
                    AssistChip(
                        onClick = { /* no-op */ },
                        label = { Text("📎") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Divider()

            MileageRow(
                item = m,
                onClick = onClick
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun ActivityDateHeader(
    date: LocalDate,
    totalAmount: Double
) {
    val formatter = remember {
        DateTimeFormatter.ofPattern("EEE d MMM") // e.g. "Mon 17 Nov"
    }
    val currency = remember {
        java.text.NumberFormat.getCurrencyInstance()
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = date.format(formatter),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = currency.format(totalAmount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun createImageUri(context: Context): Uri? {
    val name = "receipt_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Photos > Albums > SimpleExpenses
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SimpleExpenses")
        }
    }
    return context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        values
    )
}

@Composable
private fun SwipeActionBackground(direction: SwipeToDismissBoxValue?) {
    val text = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> "Edit"
        SwipeToDismissBoxValue.EndToStart -> "Delete"
        else -> ""
    }

    val bgColor = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val align = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    val textColor = when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimary
        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onError
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 16.dp),
        contentAlignment = align
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium, color = textColor)
    }
}