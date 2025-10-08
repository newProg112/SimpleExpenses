package com.example.simpleexpenses

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.simpleexpenses.ui.LocalApp
import com.example.simpleexpenses.ui.ExpenseViewModel
import com.example.simpleexpenses.ui.ExpenseEditScreen
import com.example.simpleexpenses.ui.ExpenseListScreen
import com.example.simpleexpenses.ui.ExpenseVMFactory
import com.example.simpleexpenses.ui.ExportScreen
import com.example.simpleexpenses.ui.MileageEditScreen
import com.example.simpleexpenses.ui.MileageListScreen
import com.example.simpleexpenses.ui.rememberMileageViewModel

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LocalApp

        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                val nav = rememberNavController()
                val vm: ExpenseViewModel = viewModel(factory = ExpenseVMFactory(app))

                NavHost(navController = nav, startDestination = "list") {
                    composable("list") {
                        ExpenseListScreen(
                            viewModel = vm,
                            onAdd = { nav.navigate("edit") },
                            onEdit = { id -> nav.navigate("edit?id=$id") },
                            onExport = { nav.navigate("export") },
                            onOpenMileage = { nav.navigate("mileage") }
                        )
                    }
                    composable(
                        route = "edit?id={id}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType; defaultValue = -1L }
                        )
                    ) { backStack ->
                        val id = backStack.arguments?.getLong("id") ?: -1L
                        ExpenseEditScreen(
                            viewModel = vm,
                            expenseId = if (id >= 0) id else null,
                            onDone = { nav.popBackStack() }
                        )
                    }
                    composable("export") {
                        ExportScreen(
                            viewModel = vm,
                            onBack = { nav.popBackStack() }
                        )
                    }
                    composable("mileage") {
                        val mvm = rememberMileageViewModel()
                        MileageListScreen(
                            vm = mvm,
                            onAddClick = { nav.navigate("mileage_edit?id=-1") },
                            onEdit = { id -> nav.navigate("mileage_edit?id=$id") }   // ← open editor with id
                        )
                    }

                    // mileage edit (optional id)
                    composable(
                        route = "mileage_edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
                    ) { backStack ->
                        val id = backStack.arguments?.getLong("id") ?: -1L
                        val mvm = rememberMileageViewModel()
                        MileageEditScreen(
                            vm = mvm,
                            onDone = { nav.popBackStack() },
                            editId = if (id >= 0) id else null                     // ← pass editId
                        )
                    }

                }
            }
        }
    }
}