package com.example.simpleexpenses.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.simpleexpenses.data.AppDatabase
import com.example.simpleexpenses.ui.ExpenseViewModel

class LocalApp : Application() {

    // Single DB instance
    val db: AppDatabase by lazy {
        Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "expenses.db"
        ).fallbackToDestructiveMigration().build()
    }

    // Simple factory to pass DAO into your VM
    val viewModelFactory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ExpenseViewModel::class.java)) {
                return ExpenseViewModel(db.expenseDao()) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
        }
    }
}