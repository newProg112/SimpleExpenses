package com.example.simpleexpenses.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ExpenseStatus = ExpenseStatus.Submitted,
    val category: String = "General",
    val merchant: String? = null,
    val notes: String? = null,
    val reimbursable: Boolean = true,
    val paymentMethod: String = "Personal", // keep String to avoid converters for now

    // OLD single-attachment fields (kept for now so nothing else breaks)
    val hasReceipt: Boolean = false,
    val receiptUri: String? = null,

    val attachmentUris: List<String> = emptyList(),

    // NEW: VAT tweak in pence (can be negative)
    val vatAdjustmentPence: Int = 0,

    val vatRatePercent: Int = 20, // default to 20% for existing rows

    val vatManualPence: Int? = null
)