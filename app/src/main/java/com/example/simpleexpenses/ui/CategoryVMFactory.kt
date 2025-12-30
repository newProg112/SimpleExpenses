package com.example.simpleexpenses.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.simpleexpenses.data.ExpenseCategoryDao
import com.example.simpleexpenses.data.ExpenseDao

class CategoryVMFactory(
    private val dao: ExpenseCategoryDao,
    private val expenseDao: ExpenseDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CategoryViewModel(dao, expenseDao) as T
    }
}