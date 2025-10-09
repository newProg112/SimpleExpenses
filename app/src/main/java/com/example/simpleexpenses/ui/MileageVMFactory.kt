package com.example.simpleexpenses.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simpleexpenses.data.AppDatabase

class MileageVMFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val dao = AppDatabase.get(context).mileageDao()
        return MileageViewModel(dao) as T
    }
}

@Composable
fun rememberMileageViewModel(): MileageViewModel {
    val ctx = LocalContext.current
    return viewModel(factory = MileageVMFactory(ctx))
}