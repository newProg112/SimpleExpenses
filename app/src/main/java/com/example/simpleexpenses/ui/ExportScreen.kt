package com.example.simpleexpenses.ui

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()

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
    var includeExpenses by remember { mutableStateOf(true) }
    var includeMileage by remember { mutableStateOf(true) }

    var targetUri by remember { mutableStateOf<Uri?>(null) }

    fun parseDateOrNull(text: String): LocalDate? =
        text.takeIf { it.isNotBlank() }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

    fun inRange(date: LocalDate, from: LocalDate?, to: LocalDate?): Boolean {
        val okFrom = from == null || !date.isBefore(from)
        val okTo = to == null || !date.isAfter(to)
        return okFrom && okTo
    }

    val saver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        targetUri = uri
        if (uri != null) {
            scope.launch {
                // Validate dates first
                val fromDate = parseDateOrNull(fromDateText)
                val toDate = parseDateOrNull(toDateText)

                if ((fromDateText.isNotBlank() && fromDate == null) ||
                    (toDateText.isNotBlank() && toDate == null)
                ) {
                    snackbar.showSnackbar("Please use date format YYYY-MM-DD")
                    return@launch
                }

                if (!includeExpenses && !includeMileage) {
                    snackbar.showSnackbar("Select at least one: Expenses or Mileage")
                    return@launch
                }

                // Apply filters
                val exportExpenses =
                    if (!includeExpenses) emptyList()
                    else expenses.filter { e ->
                        val eDate = e.timestamp.toLocalDate()
                        inRange(eDate, fromDate, toDate)
                    }

                val exportMileage =
                    if (!includeMileage) emptyList()
                    else mileage.filter { m ->
                        inRange(m.date, fromDate, toDate)
                    }

                try {
                    val csv = ExportCsv.buildFromExpensesAndMileage(exportExpenses, exportMileage)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    snackbar.showSnackbar(
                        "Exported ${exportExpenses.size} expenses, ${exportMileage.size} mileage rows"
                    )
                } catch (t: Throwable) {
                    snackbar.showSnackbar("Export failed: ${t.message}")
                }
            }
        }
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
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Create a CSV with your expenses and mileage, filtered by date and type.",
                style = MaterialTheme.typography.bodyMedium
            )

            // Date range
            OutlinedTextField(
                value = fromDateText,
                onValueChange = { fromDateText = it },
                label = { Text("From date (YYYY-MM-DD, optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            OutlinedTextField(
                value = toDateText,
                onValueChange = { toDateText = it },
                label = { Text("To date (YYYY-MM-DD, optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // What to include
            Text("Include in export", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeExpenses,
                    onCheckedChange = { includeExpenses = it }
                )
                Text("Expenses", modifier = Modifier.padding(start = 4.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = includeMileage,
                    onCheckedChange = { includeMileage = it }
                )
                Text("Mileage", modifier = Modifier.padding(start = 4.dp))
            }

            Button(
                onClick = { saver.launch("simple_expenses_export.csv") },
                enabled = includeExpenses || includeMileage
            ) {
                Text("Export as CSV")
            }

            if (targetUri != null) {
                Text(
                    "Last exported: $targetUri",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Text(
                "Tip: leave dates blank to export everything.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
