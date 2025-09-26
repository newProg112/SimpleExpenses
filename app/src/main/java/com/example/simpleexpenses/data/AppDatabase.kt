package com.example.simpleexpenses.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Expense::class],
    version = 2,                    // ⬅️ bump version
    exportSchema = true
)
@TypeConverters(Converters::class)   // ⬅️ register converters
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        // 1 → 2: add new columns with safe defaults
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN merchant TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN notes TEXT")
                db.execSQL("ALTER TABLE expenses ADD COLUMN reimbursable INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE expenses ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT 'Personal'")
            }
        }
    }
}