package com.example.simpleexpenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ExpenseVMFactory(private val app: LocalApp) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
            @Suppress("UNCHECKEDCAST")
            return ExpenseViewModel(app.db.expenseDao()) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}