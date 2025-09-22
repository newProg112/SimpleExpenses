package com.example.simpleexpenses.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    viewModel: ExpenseViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            Text("Generate a CSV of your expenses and share it via email, Drive, etc.")
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    scope.launch {
                        exporting = true
                        error = null
                        try {
                            val file = viewModel.exportCsv(context)
                            val authority = context.packageName + ".fileprovider"
                            val uri = FileProvider.getUriForFile(
                                context,
                                authority,
                                file
                            )
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                clipData = ClipData.newRawUri("Expenses CSV", uri)
                            }
                            context.startActivity(
                                Intent.createChooser(share, "Share expenses CSV")
                            )
                        } catch (t: Throwable) {
                            error = t.message ?: "Export failed"
                        } finally {
                            exporting = false
                        }
                    }
                },
                enabled = !exporting
            ) {
                Text(if (exporting) "Exporting…" else "Generate & Share CSV")
            }

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}