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
            // Header row
            w.appendLine(
                "Date,Title,Category,Merchant,Net,VAT,Gross,VAT rate (%),Status,Reimbursable,Payment method,Has receipt"
            )

            items.forEach { e ->
                val dateStr = dateFormat.format(Date(e.timestamp))

                // Treat stored amount as GROSS
                val gross = e.amount
                val ratePercent = e.vatRatePercent
                val rate = ratePercent / 100.0

                val net = if (rate == 0.0) {
                    gross
                } else {
                    gross / (1.0 + rate)
                }
                val vat = gross - net

                val netStr = String.format(Locale.UK, "%.2f", net)
                val vatStr = String.format(Locale.UK, "%.2f", vat)
                val grossStr = String.format(Locale.UK, "%.2f", gross)

                val reimbursableStr = if (e.reimbursable) "Yes" else "No"
                val hasReceiptStr = if (e.hasReceipt) "Yes" else "No"

                w.append(csvEscape(dateStr)).append(',')
                    .append(csvEscape(e.title)).append(',')
                    .append(csvEscape(e.category)).append(',')
                    .append(csvEscape(e.merchant.orEmpty())).append(',')
                    .append(csvEscape(netStr)).append(',')
                    .append(csvEscape(vatStr)).append(',')
                    .append(csvEscape(grossStr)).append(',')
                    .append(csvEscape(ratePercent.toString())).append(',')
                    .append(csvEscape(e.status.name)).append(',')
                    .append(csvEscape(reimbursableStr)).append(',')
                    .append(csvEscape(e.paymentMethod)).append(',')
                    .append(csvEscape(hasReceiptStr))
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