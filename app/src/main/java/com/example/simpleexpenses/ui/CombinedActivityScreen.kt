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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.IosShare
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import java.time.Instant
import java.time.ZoneId

sealed class CombinedItem {
    data class ExpenseItem(val e: Expense) : CombinedItem()
    data class MileageItem(val m: MileageEntry) : CombinedItem()
}

enum class CombinedFilter { ALL, EXPENSES, MILEAGE }

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
    onStartDraftFromCamera: (Uri) -> Unit
) {
    val expenses by expenseVM.expenses.collectAsState(initial = emptyList())
    val mileage by mileageVM.items.collectAsState(initial = emptyList())

    var filter by remember { mutableStateOf(CombinedFilter.ALL) }

    val items = remember(expenses, mileage, filter) {
        val list: List<CombinedItem> = when (filter) {
            CombinedFilter.ALL ->
                expenses.map { CombinedItem.ExpenseItem(it) } + mileage.map { CombinedItem.MileageItem(it) }
            CombinedFilter.EXPENSES ->
                expenses.map { CombinedItem.ExpenseItem(it) }
            CombinedFilter.MILEAGE ->
                mileage.map { CombinedItem.MileageItem(it) }
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

    val cameraLauncherForDraft = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCaptureUri?.let { captured ->
                // Hand this off to nav to open New Expense with the photo
                onStartDraftFromCamera(captured)
            }
        } else {
            // Clean up empty entry if the user cancelled
            pendingCaptureUri?.let { context.contentResolver.delete(it, null, null) }
        }
        pendingCaptureUri = null
    }

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                // 📷 New expense from photo
                FloatingActionButton(
                    onClick = {
                        val uri = createImageUri(context)
                        pendingCaptureUri = uri
                        if (uri != null) {
                            cameraLauncherForDraft.launch(uri)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "New expense from photo"
                    )
                }

                // Existing FABs
                FloatingActionButton(onClick = onAddExpense) { Text("£") }
                FloatingActionButton(onClick = onAddMileage) { Text("mi") }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
        ) {
            // Filter row
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            }

            Divider()

            // --- SUMMARY CALCULATIONS ---

            val now = remember { java.time.LocalDate.now() }
            val monthStart = remember { now.withDayOfMonth(1) }

            val expensesThisMonth = expenses.filter { e ->
                val d = java.time.Instant.ofEpochMilli(e.timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
                d >= monthStart
            }

            val mileageThisMonth = mileage.filter { m ->
                m.date >= monthStart
            }

            // Expenses totals
            val expenseTotalMonth = expensesThisMonth.sumOf { it.amount }

            // Mileage totals (stored in pence)
            val mileageTotalMonth = mileageThisMonth.sumOf { it.amountPence } / 100.0

            // Combined
            val combinedTotal = expenseTotalMonth + mileageTotalMonth

            // Missing receipts count
            val missingReceipts = expenses.count { !it.hasReceipt }

            val currency = remember { java.text.NumberFormat.getCurrencyInstance() }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // --- Big combined total card ---
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        Modifier.padding(16.dp)
                    ) {
                        Text("This Month", style = MaterialTheme.typography.titleMedium)
                        Text(
                            currency.format(combinedTotal),
                            style = MaterialTheme.typography.headlineLarge
                        )
                    }
                }

                // --- Row: Expenses + Mileage ---
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Expenses card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Expenses", style = MaterialTheme.typography.titleSmall)
                            Text(
                                currency.format(expenseTotalMonth),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }

                    // Mileage card
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Mileage", style = MaterialTheme.typography.titleSmall)
                            Text(
                                currency.format(mileageTotalMonth),
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                    }
                }

                // --- Missing receipts card ---
                if (missingReceipts > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Missing receipts", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "$missingReceipts expense(s)",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items) { item ->
                    when (item) {
                        is CombinedItem.ExpenseItem ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                ExpenseRow(
                                    item = item.e,
                                    onClick = { onExpenseClick(item.e.id) }
                                )
                            }

                        is CombinedItem.MileageItem ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(12.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                MileageRow(
                                    item = item.m,
                                    onClick = { onMileageClick(item.m.id) }
                                )
                            }
                    }
                }
            }
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