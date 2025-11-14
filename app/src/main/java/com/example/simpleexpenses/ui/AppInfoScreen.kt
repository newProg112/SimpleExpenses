package com.example.simpleexpenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val packageName = context.packageName

    // Use PackageManager to get version info
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val versionName = packageInfo.versionName ?: "1.0"
    val versionCode = packageInfo.longVersionCode

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App info") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Simple Expenses",
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Version: $versionName (build $versionCode)",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Simple Expenses helps you track normal expenses and mileage in one place, with receipts, HMRC mileage rates, reminders and CSV export.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Data & privacy",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Your data is stored locally on this device. You can export it to CSV for backup or for your employer. If you uninstall the app, your local data may be deleted.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Support",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "For feedback or issues, you can share this app build or contact the developer.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}