package com.example.simpleexpenses.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.simpleexpenses.data.ExpenseCategoryDao

class CategoryVMFactory(
    private val dao: ExpenseCategoryDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CategoryViewModel(dao) as T
    }
}