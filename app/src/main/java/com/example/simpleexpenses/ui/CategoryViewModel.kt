package com.example.simpleexpenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleexpenses.data.ExpenseCategory
import com.example.simpleexpenses.data.ExpenseCategoryDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val dao: ExpenseCategoryDao
) : ViewModel() {

    val categories: StateFlow<List<ExpenseCategory>> =
        dao.observeActive()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Seed defaults on first run
        viewModelScope.launch {
            if (dao.count() == 0) {
                val defaults = listOf(
                    "General",
                    "Travel",
                    "Meals",
                    "Supplies",
                    "Software",
                    "Training",
                    "Other"
                ).mapIndexed { index, name ->
                    ExpenseCategory(
                        name = name,
                        isActive = true,
                        sortOrder = index
                    )
                }
                dao.insertAll(defaults)
            }
        }
    }

    fun addCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            // Put new ones at the end
            val current = categories.value
            val nextOrder = (current.maxOfOrNull { it.sortOrder } ?: 0) + 1
            dao.upsert(
                ExpenseCategory(
                    name = trimmed,
                    isActive = true,
                    sortOrder = nextOrder
                )
            )
        }
    }

    fun setActive(category: ExpenseCategory, active: Boolean) {
        viewModelScope.launch {
            dao.upsert(category.copy(isActive = active))
        }
    }

    fun rename(category: ExpenseCategory, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            dao.upsert(category.copy(name = trimmed))
        }
    }

    fun delete(category: ExpenseCategory) {
        viewModelScope.launch {
            dao.delete(category)
        }
    }
}