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
        db = AppDatabase.get(applicationContext) // ✅ use the singleton from AppDatabase
    }
}