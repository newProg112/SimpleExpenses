package com.example.simpleexpenses.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.UK)

    /** Writes the provided items to a CSV and returns the File. */
    fun exportExpensesToCsv(context: Context, items: List<Expense>): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.UK).format(Date())
        val file = File(context.cacheDir, "expenses-$stamp.csv")

        file.bufferedWriter().use { w ->
            w.appendLine("Date,Title,Amount,Status")
            items.forEach { e ->
                val dateStr = dateFormat.format(Date(e.timestamp))
                val amountStr = String.format(Locale.UK, "%.2f", e.amount)
                w.append(csvEscape(dateStr)).append(',')
                    .append(csvEscape(e.title)).append(',')
                    .append(csvEscape(amountStr)).append(',')
                    .append(csvEscape(e.status.name))
                    .appendLine()
            }
        }
        return file
    }

    private fun csvEscape(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }
}