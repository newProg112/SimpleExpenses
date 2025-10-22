package com.example.simpleexpenses.export

import android.os.Build
import androidx.annotation.RequiresApi
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.MileageEntry
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

object ExportCsv {

    @RequiresApi(Build.VERSION_CODES.O)
    private val dateFmt = DateTimeFormatter.ISO_LOCAL_DATE
    private val currency = NumberFormat.getCurrencyInstance()

    private fun esc(s: String?): String {
        val raw = s ?: ""
        val q = raw.replace("\"", "\"\"")
        return "\"$q\""
    }

    /** Mileage-only export (what you’re using right now). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildMileageOnly(mileage: List<MileageEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("# Mileage")
        sb.appendLine("date,from,to,miles,rate_pence_per_mile,amount_pence,amount_gbp,notes")
        mileage.forEach { m ->
            val miles = ((m.distanceMeters / 1609.344) * 10.0).roundToInt() / 10.0
            val gbp = currency.format(m.amountPence / 100.0)
            sb.appendLine(
                listOf(
                    m.date.format(dateFmt),
                    esc(m.fromLabel),
                    esc(m.toLabel),
                    miles.toString(),
                    m.ratePencePerMile.toString(),
                    m.amountPence.toString(),
                    gbp,
                    esc(m.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /** Full export: Expenses + Mileage, using your Expense model (amount is a Double in £). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildFromExpensesAndMileage(expenses: List<Expense>, mileage: List<MileageEntry>): String {
        val sb = StringBuilder()

        // --- Expenses section ---
        sb.appendLine("# Expenses")
        sb.appendLine("date,category,title,merchant,amount_gbp,amount_pence,status,reimbursable,payment_method,has_receipt,receipt_uri,notes")
        expenses.forEach { e ->
            val date = LocalDate.ofInstant(Instant.ofEpochMilli(e.timestamp), ZoneId.systemDefault())
            val amountPence = (e.amount * 100.0).roundToInt()
            val gbp = currency.format(e.amount)
            sb.appendLine(
                listOf(
                    date.format(dateFmt),
                    esc(e.category),
                    esc(e.title),
                    esc(e.merchant),
                    gbp,
                    amountPence.toString(),
                    esc(e.status.name),
                    e.reimbursable.toString(),
                    esc(e.paymentMethod),
                    e.hasReceipt.toString(),
                    esc(e.receiptUri ?: ""),
                    esc(e.notes)
                ).joinToString(",")
            )
        }
        sb.appendLine()

        // --- Mileage section (same as mileage-only) ---
        sb.appendLine("# Mileage")
        sb.appendLine("date,from,to,miles,rate_pence_per_mile,amount_pence,amount_gbp,notes")
        mileage.forEach { m ->
            val miles = ((m.distanceMeters / 1609.344) * 10.0).roundToInt() / 10.0
            val gbp = currency.format(m.amountPence / 100.0)
            sb.appendLine(
                listOf(
                    m.date.format(dateFmt),
                    esc(m.fromLabel),
                    esc(m.toLabel),
                    miles.toString(),
                    m.ratePencePerMile.toString(),
                    m.amountPence.toString(),
                    gbp,
                    esc(m.notes)
                ).joinToString(",")
            )
        }

        return sb.toString()
    }
}