package com.example.simpleexpenses.ocr

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

object ReceiptOcrHelper {

    /**
     * Result from OCR:
     * - total: best guess of the total amount on the receipt
     * - date:  best guess of the receipt date as dd/MM/yyyy, or null if not found
     */
    data class ReceiptOcrResult(
        val total: Double?,
        val date: String?
    )

    /**
     * New helper: returns BOTH total + date via a callback.
     */
    fun extractTotalAndDateFromReceipt(
        context: Context,
        imageUri: Uri,
        onResult: (ReceiptOcrResult) -> Unit
    ) {
        val image = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (e: Exception) {
            Log.e("ReceiptOcrHelper", "Failed to create InputImage", e)
            onResult(ReceiptOcrResult(total = null, date = null))
            return
        }

        val recogniser = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recogniser
            .process(image)
            .addOnSuccessListener { text: Text ->
                val lines = text.textBlocks.flatMap { block ->
                    block.lines.map { it.text }
                }

                val total = findTotal(lines)
                val date = findDate(lines)

                onResult(ReceiptOcrResult(total = total, date = date))
            }
            .addOnFailureListener { e ->
                Log.e("ReceiptOcrHelper", "OCR failed", e)
                onResult(ReceiptOcrResult(total = null, date = null))
            }
    }

    /**
     * Back-compat helper – still works like your old one.
     * Any existing calls to extractTotalFromReceipt keep working.
     */
    fun extractTotalFromReceipt(
        context: Context,
        imageUri: Uri,
        onResult: (Double?) -> Unit
    ) {
        extractTotalAndDateFromReceipt(context, imageUri) { result ->
            onResult(result.total)
        }
    }

    // ------------------
    //  PRIVATE HELPERS
    // ------------------

    // Your existing "find total" logic, just moved here
    private fun findTotal(lines: List<String>): Double? {
        val amountRegex = Regex("""(\d+[\.,]\d{2})""")

        // Prefer lines mentioning "total"
        val candidateLines = lines.filter {
            it.contains("total", ignoreCase = true)
        } + lines // fallback: any line

        for (line in candidateLines) {
            val cleaned = line
                .replace("£", "")
                .replace("GBP", "", ignoreCase = true)

            val match = amountRegex.find(cleaned)
            if (match != null) {
                val raw = match.groupValues[1].replace(",", ".")
                val value = raw.toDoubleOrNull()
                if (value != null) return value
            }
        }

        return null
    }

    /**
     * Tries to spot a date on the receipt.
     * Looks for:
     *  - dd/MM/yyyy, dd-MM-yyyy, dd.MM.yyyy or dd/MM/yy, dd-MM-yy, dd.MM.yy
     *  - yyyy-MM-dd or yyyy/MM/dd
     * Returns a normalised "dd/MM/yyyy" string, or null.
     */
    private fun findDate(lines: List<String>): String? {
        val normalised = lines
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        // Priority: lines that explicitly look like a date label
        val sortedLines = normalised.sortedBy { line ->
            when {
                line.contains("date", ignoreCase = true) -> 0
                line.contains("tax point", ignoreCase = true) -> 1
                else -> 2
            }
        }

        val dmyRegex = Regex("""\b(\d{1,2})[\/\-\.](\d{1,2})[\/\-\.](\d{2,4})\b""")   // 31/12/24 or 31-12-2024
        val ymdRegex = Regex("""\b(\d{4})[\/\-\.](\d{1,2})[\/\-\.](\d{1,2})\b""")     // 2024-12-31

        for (line in sortedLines) {
            // Try dd/MM/yyyy-style first
            dmyRegex.find(line)?.let { m ->
                val day = m.groupValues[1].toIntOrNull() ?: return@let
                val month = m.groupValues[2].toIntOrNull() ?: return@let
                var year = m.groupValues[3].toIntOrNull() ?: return@let

                if (year < 100) year += 2000 // assume 20xx (e.g. 24 -> 2024)
                if (month in 1..12 && day in 1..31) {
                    return "%02d/%02d/%04d".format(day, month, year)
                }
            }

            // Then try yyyy-MM-dd-style
            ymdRegex.find(line)?.let { m ->
                val year = m.groupValues[1].toIntOrNull() ?: return@let
                val month = m.groupValues[2].toIntOrNull() ?: return@let
                val day = m.groupValues[3].toIntOrNull() ?: return@let

                if (month in 1..12 && day in 1..31) {
                    return "%02d/%02d/%04d".format(day, month, year)
                }
            }
        }

        return null
    }
}