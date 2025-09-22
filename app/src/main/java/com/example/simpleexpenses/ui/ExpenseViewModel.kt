package com.example.simpleexpenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseDao
import kotlinx.coroutines.flow.stateIn

class ExpenseViewModel(
    private val expenseDao: ExpenseDao
) : ViewModel() {

    val expenses: StateFlow<List<Expense>> =
        expenseDao.observeAll() // or getAllFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(expense: Expense) = viewModelScope.launch {
        expenseDao.insert(expense)
    }

    suspend fun get(id: Long): Expense? = expenseDao.getById(id)

    fun update(expense: Expense) = viewModelScope.launch {
        expenseDao.update(expense)
    }

    fun delete(expense: Expense) = viewModelScope.launch {
        expenseDao.delete(expense)
    }
}