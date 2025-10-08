package com.example.simpleexpenses.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Expense::class, MileageEntry::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun mileageDao(): MileageDao

    companion object {
        // 1 → 2 adds: category (TEXT NOT NULL DEFAULT 'General'),
        //             status (TEXT NOT NULL DEFAULT 'Submitted'),
        //             hasReceipt (INTEGER NOT NULL DEFAULT 0)
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // If columns already exist this will throw; run this migration only if you truly were on v1.
                db.execSQL("ALTER TABLE expenses ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN status TEXT NOT NULL DEFAULT 'Submitted'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN hasReceipt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN receiptUri TEXT")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expenses.db"
                )
                    // Choose ONE of the following:
                    // .addMigrations(MIGRATION_1_2)        // ✅ keeps data if you were on v1
                    .fallbackToDestructiveMigration()  // 💥 dev-only: wipes DB on schema change
                    .build()
                    .also { INSTANCE = it }
            }
    }
}