package com.example.simpleexpenses.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseDao
import com.example.simpleexpenses.data.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpenseFilters(
    val category: String? = null,
    val status: com.example.simpleexpenses.data.ExpenseStatus? = null,
    val hasReceipt: Boolean? = null,
    val fromDate: Long? = null,   // optional for later
    val toDate: Long? = null      // optional for later
)

class ExpenseViewModel(
    private val expenseDao: ExpenseDao
) : ViewModel() {

    // --- Reactive filters state ---
    private val _filters = MutableStateFlow(ExpenseFilters())
    val filters: StateFlow<ExpenseFilters> = _filters.asStateFlow()

    // Live list tied to filters → uses DAO.filtered(...)
    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<Expense>> =
        _filters
            .flatMapLatest { f ->
                expenseDao.filtered(
                    category = f.category,
                    status = f.status,
                    fromDate = f.fromDate,
                    toDate = f.toDate,
                    hasReceipt = f.hasReceipt
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Setters the UI calls ---
    fun setCategory(value: String?) {
        _filters.update { it.copy(category = value?.ifBlank { null }) }
    }
    fun setStatus(value: com.example.simpleexpenses.data.ExpenseStatus?) {
        _filters.update { it.copy(status = value) }
    }
    fun setHasReceipt(value: Boolean?) {
        _filters.update { it.copy(hasReceipt = value) }
    }
    fun clearFilters() {
        _filters.value = ExpenseFilters()
    }

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

    /*
    fun exportAll(context: Context, onResult: (Uri?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = expenseDao.observeAll().first()
            val uri = ExportUtils.exportExpensesToCsv(context, all)
            withContext(Dispatchers.Main) { onResult(uri) }
        }
    }

     */

    fun exportPaidCsv(context: Context, onResult: (File?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val paid = expenseDao.getPaidExpenses()   // uses WHERE status = 'Paid'
                val file = ExportUtils.exportExpensesToCsv(context, paid)
                withContext(Dispatchers.Main) { onResult(file) }
            } catch (t: Throwable) {
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    suspend fun suggestMerchants(prefix: String): List<String> =
        expenseDao.suggestMerchants(prefix)

    /*
    suspend fun exportCsv(context: Context): File = withContext(Dispatchers.IO) {
        // Fetch a one-shot snapshot from the DAO
        val list = expenseDao.observeAll().first()

        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.UK).format(Date())
        val file = File(context.cacheDir, "expenses-$stamp.csv")

        file.bufferedWriter().use { w ->
            w.appendLine("id,title,amount,timestamp")
            list.forEach { e ->
                w.append(e.id.toString()).append(',')
                    .append(csvEscape(e.title)).append(',')
                    .append(e.amount.toString()).append(',')
                    .append(e.timestamp.toString())
                    .appendLine()
            }
        }
        file
    }
    */
}

private fun csvEscape(s: String): String {
    val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
    val escaped = s.replace("\"", "\"\"")
    return if (needsQuote) "\"$escaped\"" else escaped
}