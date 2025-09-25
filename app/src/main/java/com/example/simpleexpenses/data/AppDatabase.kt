package com.example.simpleexpenses.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Expense::class],
    version = 2,                    // ⬅️ bump version
    exportSchema = true
)
@TypeConverters(Converters::class)   // ⬅️ register converters
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}