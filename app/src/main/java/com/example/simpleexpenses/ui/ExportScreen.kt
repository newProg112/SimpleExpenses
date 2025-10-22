package com.example.simpleexpenses.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simpleexpenses.data.AppDatabase
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.MileageEntry
import com.example.simpleexpenses.export.ExportCsv
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter

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

    // Collect mileage using a local MileageVM
    val db = remember { AppDatabase.get(context.applicationContext) }
    val mileageVM: MileageViewModel = viewModel(
        factory = MileageVMFactory(context.applicationContext, db.mileageDao())
    )
    val mileage: List<MileageEntry> by mileageVM.items.collectAsState()

    var targetUri by remember { mutableStateOf<Uri?>(null) }

    val saver = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        targetUri = uri
        if (uri != null) {
            scope.launch {
                try {
                    val csv = ExportCsv.buildMileageOnly(mileage)
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(csv.toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    snackbar.showSnackbar("Exported to: ${uri.lastPathSegment ?: "file"}")
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
            Text("Create a CSV with all expenses and mileage.")

            Text(
                "Will export: ${mileage.size} mileage rows",
                style = MaterialTheme.typography.labelLarge
            )

            Button(
                onClick = { saver.launch("simple_expenses_export.csv") }
            ) { Text("Export as CSV") }

            if (targetUri != null) {
                Text(
                    "Last exported: $targetUri",
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}