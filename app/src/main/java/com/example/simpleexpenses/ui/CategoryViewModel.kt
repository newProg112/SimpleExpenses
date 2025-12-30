package com.example.simpleexpenses.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleexpenses.data.ExpenseCategory
import com.example.simpleexpenses.data.ExpenseCategoryDao
import com.example.simpleexpenses.data.ExpenseDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoryViewModel(
    private val dao: ExpenseCategoryDao,
    private val expenseDao: ExpenseDao
) : ViewModel() {

    // (optional but useful) a message you can show in the UI
    private val _uiMessage = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val uiMessage: kotlinx.coroutines.flow.StateFlow<String?> = _uiMessage

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

    fun clearMessage() {
        _uiMessage.value = null
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
            val usedCount = expenseDao.countByCategory(category.name)
            if (usedCount > 0) {
                _uiMessage.value = "Can't delete “${category.name}” — it's used by $usedCount expense(s)."
                return@launch
            }
            dao.delete(category)
        }
    }

    fun moveUp(category: ExpenseCategory) {
        viewModelScope.launch {
            val list = dao.getActiveOrderedOnce()
            val idx = list.indexOfFirst { it.id == category.id }
            if (idx <= 0) return@launch

            val above = list[idx - 1]
            val current = list[idx]

            // swap sortOrder
            dao.update(above.copy(sortOrder = current.sortOrder))
            dao.update(current.copy(sortOrder = above.sortOrder))
        }
    }

    fun moveDown(category: ExpenseCategory) {
        viewModelScope.launch {
            val list = dao.getActiveOrderedOnce()
            val idx = list.indexOfFirst { it.id == category.id }
            if (idx == -1 || idx >= list.lastIndex) return@launch

            val below = list[idx + 1]
            val current = list[idx]

            // swap sortOrder
            dao.update(below.copy(sortOrder = current.sortOrder))
            dao.update(current.copy(sortOrder = below.sortOrder))
        }
    }
}