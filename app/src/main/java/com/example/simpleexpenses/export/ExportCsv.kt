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

    @RequiresApi(Build.VERSION_CODES.O)
    fun buildMileageOnly(mileage: List<MileageEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("# Mileage")
        sb.appendLine("date,from,to,miles,rate_pence_per_mile,net_gbp,vat_gbp,gross_gbp,net_pence,vat_pence,gross_pence,notes")

        mileage.forEach { m ->
            val miles = ((m.distanceMeters / 1609.344) * 10.0).roundToInt() / 10.0

            // Treat amountPence as gross
            val gross = m.amountPence / 100.0
            val net = gross / 1.20
            val vat = gross - net

            val grossPence = m.amountPence
            val netPence = (net * 100.0).roundToInt()
            val vatPence = (vat * 100.0).roundToInt()

            sb.appendLine(
                listOf(
                    m.date.format(dateFmt),
                    esc(m.fromLabel),
                    esc(m.toLabel),
                    miles.toString(),
                    m.ratePencePerMile.toString(),

                    // NEW: Net / VAT / Gross (GBP)
                    currency.format(net),
                    currency.format(vat),
                    currency.format(gross),

                    // NEW: Net / VAT / Gross (pence)
                    netPence.toString(),
                    vatPence.toString(),
                    grossPence.toString(),

                    esc(m.notes)
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    /** Full export: Expenses + Mileage, using your Expense model (amount is a Double in £). */
    @RequiresApi(Build.VERSION_CODES.O)
    fun buildFromExpensesAndMileage(
        expenses: List<Expense>,
        mileage: List<MileageEntry>
    ): String {
        val sb = StringBuilder()

        // --- Expenses section ---
        sb.appendLine("# Expenses")
        sb.appendLine(
            "date,category,title,merchant,net_gbp,vat_gbp,gross_gbp,vat_rate_percent,net_pence,vat_pence,gross_pence,status,reimbursable,payment_method,has_receipt,receipt_uri,notes"
        )

        expenses.forEach { e ->
            val date = LocalDate.ofInstant(
                Instant.ofEpochMilli(e.timestamp),
                ZoneId.systemDefault()
            )

            val gross = e.amount
            val ratePercent = e.vatRatePercent
            val rate = ratePercent / 100.0

            // Base NET/VAT from rate
            val baseNet = if (rate == 0.0) {
                gross
            } else {
                gross / (1.0 + rate)
            }
            val baseVat = gross - baseNet

            // Apply stored adjustment (in pence) to VAT, then back-calc NET
            val vatAdjustment = e.vatAdjustmentPence / 100.0
            val vat = baseVat + vatAdjustment
            val net = gross - vat

            val grossPence = (gross * 100).roundToInt()
            val netPence = (net * 100).roundToInt()
            val vatPence = (vat * 100).roundToInt()

            sb.appendLine(
                listOf(
                    date.format(dateFmt),
                    esc(e.category),
                    esc(e.title),
                    esc(e.merchant),

                    // Net / VAT / Gross (formatted)
                    currency.format(net),
                    currency.format(vat),
                    currency.format(gross),

                    // NEW column: vat_rate_percent
                    ratePercent.toString(),

                    // Pence versions
                    netPence.toString(),
                    vatPence.toString(),
                    grossPence.toString(),

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

        // --- Mileage section (still uses fixed 20% VAT on mileage amounts) ---
        sb.appendLine("# Mileage")
        sb.appendLine(
            "date,from,to,miles,rate_pence_per_mile,net_gbp,vat_gbp,gross_gbp,net_pence,vat_pence,gross_pence,notes"
        )

        mileage.forEach { m ->
            val miles = ((m.distanceMeters / 1609.344) * 10.0).roundToInt() / 10.0

            // Treat amountPence as gross
            val gross = m.amountPence / 100.0
            val net = gross / 1.20
            val vat = gross - net

            val grossPence = m.amountPence
            val netPence = (net * 100.0).roundToInt()
            val vatPence = (vat * 100.0).roundToInt()

            sb.appendLine(
                listOf(
                    m.date.format(dateFmt),
                    esc(m.fromLabel),
                    esc(m.toLabel),
                    miles.toString(),
                    m.ratePencePerMile.toString(),

                    // Net / VAT / Gross (GBP)
                    currency.format(net),
                    currency.format(vat),
                    currency.format(gross),

                    // Net / VAT / Gross (pence)
                    netPence.toString(),
                    vatPence.toString(),
                    grossPence.toString(),

                    esc(m.notes)
                ).joinToString(",")
            )
        }

        return sb.toString()
    }
}