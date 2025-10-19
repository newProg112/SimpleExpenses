package com.example.simpleexpenses.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.simpleexpenses.data.MileageDao
import com.example.simpleexpenses.prefs.SettingsRepository

class MileageVMFactory(
    private val appContext: Context,
    private val dao: MileageDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val settingsRepo = SettingsRepository(appContext)
        return MileageViewModel(dao, settingsRepo) as T
    }
}