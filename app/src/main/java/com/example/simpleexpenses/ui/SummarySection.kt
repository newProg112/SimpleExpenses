package com.example.simpleexpenses.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.ExpenseStatus
import java.text.NumberFormat
import java.util.Locale

@Composable
fun SummarySection(
    submittedTotal: Double,
    approvedTotal: Double,
    paidTotal: Double,
    submittedCount: Int,
    approvedCount: Int,
    paidCount: Int,
    selected: ExpenseStatus?,                    // ← which status is currently selected (or null)
    onCardClick: (ExpenseStatus) -> Unit,        // ← tell parent which card was tapped
    modifier: Modifier = Modifier
) {
    val money = NumberFormat.getCurrencyInstance(Locale.UK)

    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            "Summary",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SummaryCard(
                title = "Submitted",
                amountFormatted = money.format(submittedTotal),
                count = submittedCount,
                icon = Icons.Filled.Send,
                color = Color(0xFF2196F3),
                selected = selected == ExpenseStatus.Submitted,
                onClick = { onCardClick(ExpenseStatus.Submitted) },
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Approved",
                amountFormatted = money.format(approvedTotal),
                count = approvedCount,
                icon = Icons.Filled.CheckCircle,
                color = Color(0xFF4CAF50),
                selected = selected == ExpenseStatus.Approved,
                onClick = { onCardClick(ExpenseStatus.Approved) },
                modifier = Modifier.weight(1f)
            )
            SummaryCard(
                title = "Paid",
                amountFormatted = money.format(paidTotal),
                count = paidCount,
                icon = Icons.Filled.AttachMoney,
                color = Color(0xFFFFC107),
                selected = selected == ExpenseStatus.Paid,
                onClick = { onCardClick(ExpenseStatus.Paid) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    amountFormatted: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = if (selected) color.copy(alpha = 0.18f) else color.copy(alpha = 0.08f)
    val border = if (selected) BorderStroke(1.dp, color) else null

    Card(
        onClick = onClick,
        modifier = modifier,
        border = border,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).animateContentSize(), // smooth size change on select
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = title, tint = color, modifier = Modifier.size(28.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, color = color)
            Text(
                amountFormatted,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color
            )
            Text(
                text = "$count ${if (count == 1) "item" else "items"}",
                style = MaterialTheme.typography.labelMedium,
                color = color.copy(alpha = 0.9f)
            )
        }
    }
}