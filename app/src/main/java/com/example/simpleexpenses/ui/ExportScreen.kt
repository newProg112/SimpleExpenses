package com.example.simpleexpenses.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.simpleexpenses.data.Expense
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lastMessage by remember { mutableStateOf<String?>(null) }

    val suggestedName = remember {
        val d = java.time.LocalDate.now()
        "expenses-$d.csv"
    }

    val createCsv = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) {
            // user cancelled — just go back
            onBack()
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            val rows = viewModel.exportSnapshot()
            val ok = writeExpensesCsv(context, uri.toString(), rows)
            lastMessage = if (ok) "Exported ${rows.size} rows" else "Export failed"
            onBack()
        }
    }

    // Auto-launch once when arriving on this screen
    LaunchedEffect(Unit) { createCsv.launch(suggestedName) }

    // Minimal fallback UI if needed
    Scaffold(topBar = { TopAppBar(title = { Text("Export CSV") }) }) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Choose where to save your CSV.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = { createCsv.launch(suggestedName) }) { Text("Save CSV") }
            lastMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(msg, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun writeExpensesCsv(
    context: Context,
    docUri: String,
    rows: List<Expense>
): Boolean {
    return try {
        val uri = android.net.Uri.parse(docUri)
        context.contentResolver.openOutputStream(uri)?.use { os ->
            OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                fun esc(v: String?): String {
                    if (v == null) return ""
                    val needsQuote = v.any { it == ',' || it == '"' || it == '\n' || it == '\r' } ||
                            v.startsWith(' ') || v.endsWith(' ')
                    return if (needsQuote) "\"" + v.replace("\"", "\"\"") + "\"" else v
                }

                // Header (aligns with your Expense fields)
                w.appendLine(
                    listOf(
                        "id","title","amount","status","category","merchant","notes",
                        "reimbursable","paymentMethod","hasReceipt","receiptUri",
                        "timestamp","timestamp_local"
                    ).joinToString(",")
                )

                val zone = java.time.ZoneId.systemDefault()
                val fmt  = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME

                // Rows
                rows.forEach { e ->
                    val localTime = java.time.Instant.ofEpochMilli(e.timestamp)
                        .atZone(zone).toLocalDateTime().format(fmt)

                    w.appendLine(
                        listOf(
                            e.id.toString(),
                            esc(e.title),
                            e.amount.toString(),
                            e.status.name,
                            esc(e.category),
                            esc(e.merchant),
                            esc(e.notes),
                            e.reimbursable.toString(),
                            esc(e.paymentMethod),
                            e.hasReceipt.toString(),
                            esc(e.receiptUri),
                            e.timestamp.toString(),
                            esc(localTime)
                        ).joinToString(",")
                    )
                }
            }
        }
        true
    } catch (_: Throwable) {
        false
    }
}