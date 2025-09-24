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

    private fun csvEscape(value: String): String {
        // If value contains comma, quote, or newline -> wrap in quotes and escape quotes
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        if (!needsQuoting) return value
        val doubled = value.replace("\"", "\"\"")
        return "\"$doubled\""
    }

    fun exportExpensesToCsv(context: Context, expenses: List<Expense>): Uri? {
        return try {
            val csvFile = File(context.cacheDir, "expenses.csv")
            FileOutputStream(csvFile).use { fos ->
                // If Excel compatibility is needed, uncomment BOM:
                // fos.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM

                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val amountFormat = DecimalFormat("0.00")

                    // Header
                    writer.append("Date,Title,Amount\n")

                    for (e in expenses) {
                        val date = csvEscape(dateFormat.format(Date(e.timestamp)))
                        val title = csvEscape(e.title)
                        val amount = amountFormat.format(e.amount) // numeric: no need to quote
                        writer.append("$date,$title,$amount\n")
                    }
                    writer.flush()
                }
            }

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }
}