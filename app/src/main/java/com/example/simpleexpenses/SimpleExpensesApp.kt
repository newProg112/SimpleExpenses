package com.example.simpleexpenses

import android.app.Application
import androidx.room.Room
import com.example.simpleexpenses.data.AppDatabase
import com.example.simpleexpenses.data.MIGRATION_1_2

/*
class SimpleExpensesApp {

    // Expose DB as a singleton for the app
    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "simple-expenses.db"
        )
            .addMigrations(MIGRATION_1_2)            // <- keep data
            // .fallbackToDestructiveMigration()      // <- dev only (wipes data on schema change)
            .build()
    }

}

 */