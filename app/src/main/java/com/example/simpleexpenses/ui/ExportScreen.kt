package com.example.simpleexpenses.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simpleexpenses.data.AppDatabase
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.MileageEntry
import com.example.simpleexpenses.export.ExportCsv
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// Helper: convert Expense.date (epoch millis) -> LocalDate
@RequiresApi(Build.VERSION_CODES.O)
fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

private enum class ExportKind {
    COMBINED, EXPENSES, MILEAGE
}

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun ExportScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // All expenses from main VM
    val expenses: List<Expense> by viewModel.expenses.collectAsState()

    // All mileage via a local VM
    val db = remember { AppDatabase.get(context.applicationContext) }
    val mileageVM: MileageViewModel = viewModel(
        factory = MileageVMFactory(context.applicationContext, db.mileageDao())
    )
    val mileage: List<MileageEntry> by mileageVM.items.collectAsState()

    // Export options
    var fromDateText by remember { mutableStateOf("") }   // "YYYY-MM-DD"
    var toDateText by remember { mutableStateOf("") }
    var useDateRange by remember { mutableStateOf(false) }

    var exportKind by remember { mutableStateOf<ExportKind?>(null) }
    var pendingFileName by remember { mutableStateOf<String?>(null) }
    var lastExportSummary by remember { mutableStateOf<String?>(null) }

    fun parseDateOrNull(text: String): LocalDate? =
        text.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun inRange(date: LocalDate, from: LocalDate?, to: LocalDate?): Boolean {
        val okFrom = from == null || !date.isBefore(from)
        val okTo = to == null || !date.isAfter(to)
        return okFrom && okTo
    }

    fun buildFileName(kind: ExportKind): String {
        val dateStr = LocalDate.now().toString()
        return when (kind) {
            ExportKind.COMBINED -> "SimpleExpenses_Combined_$dateStr.csv"
            ExportKind.EXPENSES -> "SimpleExpenses_Expenses_$dateStr.csv"
            ExportKind.MILEAGE -> "SimpleExpenses_Mileage_$dateStr.csv"
        }
    }

    val saver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        val kind = exportKind
        val fileName = pendingFileName

        if (uri != null && kind != null) {
            scope.launch {
                // Validate dates if we're using a range
                val fromDate = if (useDateRange) parseDateOrNull(fromDateText) else null
                val toDate = if (useDateRange) parseDateOrNull(toDateText) else null

                if (useDateRange &&
                    ((fromDateText.isNotBlank() && fromDate == null) ||
                            (toDateText.isNotBlank() && toDate == null))
                ) {
                    snackbar.showSnackbar("Please use date format YYYY-MM-DD")
                    return@launch
                }

                // Apply filters
                val exportExpenses =
                    when (kind) {
                        ExportKind.EXPENSES, ExportKind.COMBINED -> {
                            expenses.filter { e ->
                                val eDate = e.timestamp.toLocalDate()
                                inRange(eDate, fromDate, toDate)
                            }
                        }
                        ExportKind.MILEAGE -> emptyList()
                        null -> emptyList()
                    }

                val exportMileage =
                    when (kind) {
                        ExportKind.MILEAGE, ExportKind.COMBINED -> {
                            mileage.filter { m ->
                                val mDate = m.date
                                inRange(mDate, fromDate, toDate)
                            }
                        }
                        ExportKind.EXPENSES -> emptyList()
                        null -> emptyList()
                    }

                if (exportExpenses.isEmpty() && exportMileage.isEmpty()) {
                    snackbar.showSnackbar(
                        if (useDateRange) {
                            "Nothing to export in this date range"
                        } else {
                            "Nothing to export"
                        }
                    )
                    return@launch
                }

                try {
                    val csv = ExportCsv.buildFromExpensesAndMileage(exportExpenses, exportMileage)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }

                    val summary = when (kind) {
                        ExportKind.COMBINED ->
                            "Exported ${exportExpenses.size} expenses and ${exportMileage.size} mileage rows"
                        ExportKind.EXPENSES ->
                            "Exported ${exportExpenses.size} expenses"
                        ExportKind.MILEAGE ->
                            "Exported ${exportMileage.size} mileage rows"
                    } + (if (fileName != null) " → $fileName" else "")

                    lastExportSummary = summary
                    snackbar.showSnackbar(summary)
                } catch (t: Throwable) {
                    snackbar.showSnackbar("Export failed: ${t.message}")
                }
            }
        }
    }

    fun startExport(kind: ExportKind) {
        exportKind = kind
        val name = buildFileName(kind)
        pendingFileName = name
        saver.launch(name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export CSV") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { pad ->
        val scrollState = rememberScrollState()

        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Create CSV files you can open in Excel, Numbers or Google Sheets.",
                style = MaterialTheme.typography.bodyMedium
            )

            // Date range toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Limit by date range",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        if (useDateRange)
                            "Only export rows between the dates below."
                        else
                            "Turn on to export just a specific period. Off = export everything.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = useDateRange,
                    onCheckedChange = { useDateRange = it }
                )
            }

            OutlinedTextField(
                value = fromDateText,
                onValueChange = { fromDateText = it },
                label = { Text("From date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = useDateRange
            )
            OutlinedTextField(
                value = toDateText,
                onValueChange = { toDateText = it },
                label = { Text("To date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = useDateRange
            )

            Spacer(modifier = Modifier.height(8.dp))

            // --- Live preview of what will be exported ---
            val previewFromDate = if (useDateRange) parseDateOrNull(fromDateText) else null
            val previewToDate = if (useDateRange) parseDateOrNull(toDateText) else null

            val hasDateError =
                useDateRange && (
                        (fromDateText.isNotBlank() && previewFromDate == null) ||
                                (toDateText.isNotBlank() && previewToDate == null)
                        )

            val previewExpenseCount =
                if (!hasDateError) {
                    expenses.count { e -> inRange(e.timestamp.toLocalDate(), previewFromDate, previewToDate) }
                } else 0

            val previewMileageCount =
                if (!hasDateError) {
                    mileage.count { m -> inRange(m.date, previewFromDate, previewToDate) }
                } else 0

            if (hasDateError) {
                Text(
                    "Dates look invalid – use YYYY-MM-DD (for example 2025-01-31).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = if (useDateRange) {
                        "This range currently includes $previewExpenseCount expenses and $previewMileageCount mileage rows."
                    } else {
                        "You currently have ${expenses.size} expenses and ${mileage.size} mileage rows available to export."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val nothingToExport =
                !hasDateError && previewExpenseCount == 0 && previewMileageCount == 0

            if (!hasDateError && useDateRange && nothingToExport) {
                Text(
                    "No entries in this period.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "What would you like to export?",
                style = MaterialTheme.typography.titleMedium
            )

            // Combined export
            Text(
                "Expenses + mileage (single CSV)",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Columns include: date, description, category, NET, VAT, GROSS, mileage details where applicable.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { startExport(ExportKind.COMBINED) },
                enabled = !hasDateError && (previewExpenseCount > 0 || previewMileageCount > 0),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export expenses + mileage")
            }

            // Expenses only
            Text(
                "Expenses only",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Columns include: date, description, category, NET, VAT, GROSS, payment and receipt info.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { startExport(ExportKind.EXPENSES) },
                enabled = !hasDateError && previewExpenseCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export expenses")
            }

            // Mileage only
            Text(
                "Mileage only",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Columns include: date, from → to, miles, rate and amount.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { startExport(ExportKind.MILEAGE) },
                enabled = !hasDateError && previewMileageCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Export mileage")
            }

            Text(
                "Tip: leave date range off to export everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                "After exporting, you can find your CSV in the folder you chose in the save dialog, " +
                        "using the Files app (e.g. Downloads, Documents or cloud storage).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Last export summary (optional)
            if (lastExportSummary != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Last export",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = lastExportSummary!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}