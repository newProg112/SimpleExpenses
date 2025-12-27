package com.example.simpleexpenses.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome to Simple Expenses") }
            )
        }
    ) { inner ->
        val scroll = rememberScrollState()

        Column(
            modifier = Modifier
                .padding(inner)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxSize()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Get set up in a minute",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Simple Expenses helps you capture receipts and mileage, and create exports you can actually use at work or for your own records.",
                        style = MaterialTheme.typography.bodyMedium
            )

            // TODO(widget): Re-enable when widget is back in MVP
            // OnboardingCard(
            //     title = "Quick add from your home screen",
            //     body = "Use the home screen widget to add a new expense with the camera, or log mileage with the steering wheel icon. Your totals and missing receipts show on the widget too."
            // )

            OnboardingCard(
                title = "Activity view",
                body = "The Activity screen shows expenses and mileage together, grouped by day. Tap an item to edit, or use the buttons to add new entries."
            )

            OnboardingCard(
                title = "Mileage & HMRC rates",
                body = "In Settings you can use HMRC mileage rates or set your own custom rate. Mileage totals are included in your monthly figures and exports."
            )

            OnboardingCard(
                title = "Exports made simple",
                body = "Use the export screen to create files you can send to your manager, accountant or keep for your own records."
            )

            OnboardingCard(
                title = "You stay in control",
                body = "Change mileage rate and reminders in Settings whenever you like. You can always come back here from the app menu in a future version."
            )

            // TODO(theme): Reword this card when theme switching exists
            // OnboardingCard(
            //     title = "You stay in control",
            //     body = "Change theme, mileage rate and reminders in Settings whenever you like. You can always come back here from the app menu in a future version."
            // )

            Spacer(modifier = Modifier.weight(1f, fill = true))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onFinished,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                ) {
                    Text("Get started")
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    title: String,
    body: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start
            )
        }
    }
}