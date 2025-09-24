package com.example.simpleexpenses.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Add a NOT NULL TEXT column with default 'Submitted'
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE expenses ADD COLUMN status TEXT NOT NULL DEFAULT 'Submitted'"
        )
    }
}