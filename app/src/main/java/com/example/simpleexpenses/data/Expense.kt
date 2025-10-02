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
    val hasReceipt: Boolean = false
)