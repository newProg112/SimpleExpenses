package com.example.simpleexpenses.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExpenseStatus { Submitted, Approved, Paid }

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val status: ExpenseStatus = ExpenseStatus.Submitted
)