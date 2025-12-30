package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.simpleexpenses.data.AppDatabase

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MileageRoute(onDone: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val db: AppDatabase = remember { AppDatabase.get(context) }
    val vm: MileageViewModel = viewModel(
        factory = MileageVMFactory(context, db.mileageDao())
    )
    MileageEditScreen(vm = vm, onDone = onDone)
}