package com.example.simpleexpenses.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import com.example.simpleexpenses.data.AppDatabase
import com.example.simpleexpenses.data.MIGRATION_1_2
import com.example.simpleexpenses.ui.ExpenseViewModel

class LocalApp : Application() {

    lateinit var db: AppDatabase
        private set

    override fun onCreate() {
        super.onCreate()

        db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "simple-expenses.db"
        )
            // Use ONE of these:
            .addMigrations(MIGRATION_1_2)        // keep existing data (requires you defined MIGRATION_1_2)
            // .fallbackToDestructiveMigration()  // dev shortcut: wipes DB on schema change
            .build()
    }
}