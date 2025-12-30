package com.example.simpleexpenses.ui

/**
 * Simple in-memory defaults for new expenses.
 * These last for the lifetime of the app process.
 *
 * Later on we can persist these via DataStore if you want.
 */
object ExpenseDefaults {
    var category: String = "General"
    var reimbursable: Boolean = true
    var paymentMethod: String = "Personal"
    var vatRatePercent: Int = 20
}