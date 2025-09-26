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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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