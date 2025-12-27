package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
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
import com.google.android.datatransport.BuildConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppInfoScreen(
    onBack: () -> Unit
) {
    val appName = "Simple Expenses"
    val versionName = BuildConfig.VERSION_NAME
    val versionCode = BuildConfig.VERSION_CODE
    val buildType = BuildConfig.BUILD_TYPE
    val applicationId = BuildConfig.APPLICATION_ID

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
                text = appName,
                style = MaterialTheme.typography.headlineSmall
            )

            Text(
                text = "Version: $versionName (build $versionCode)",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Build type: $buildType",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Package: $applicationId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "Simple Expenses helps you track normal expenses and mileage in one place, " +
                        "with receipts, HMRC mileage rates, reminders and CSV export.",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Data & privacy",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Your data is stored locally on this device. You can export it to CSV for backup " +
                        "or for your employer. If you uninstall the app, your local data may be deleted.",
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

            /*
            Text(
                text = "Made by Adam Elvin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
             */
        }
    }
}