package com.example.simpleexpenses

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
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
import com.example.simpleexpenses.ui.MileageRoute

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
                            onOpenMileage = { nav.navigate("mileage_list") },
                            onOpenSettings = { nav.navigate("settings") }
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

                    // mileage: open editor
                    composable("mileage") {
                        MileageRoute(onDone = { nav.popBackStack() })
                    }

                    composable("mileage_list") {
                        val context = LocalContext.current.applicationContext
                        val db = remember { com.example.simpleexpenses.data.AppDatabase.get(context) }
                        val mvm = viewModel<com.example.simpleexpenses.ui.MileageViewModel>(
                            factory = com.example.simpleexpenses.ui.MileageVMFactory(context, db.mileageDao())
                        )
                        com.example.simpleexpenses.ui.MileageListScreen(
                            vm = mvm,
                            onAddClick = { nav.navigate("mileage") },
                            onEdit = { id -> nav.navigate("mileage_edit?id=$id") }
                        )
                    }

                    // mileage edit with optional id
                    composable(
                        route = "mileage_edit?id={id}",
                        arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L })
                    ) {
                        // You can read the id if you later extend MileageRoute to accept it.
                        MileageRoute(onDone = { nav.popBackStack() })
                    }

                    composable("settings") {
                        // reuse the same MileageViewModel used elsewhere
                        val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
                        val db = remember { com.example.simpleexpenses.data.AppDatabase.get(context) }
                        val mvm = androidx.lifecycle.viewmodel.compose.viewModel<com.example.simpleexpenses.ui.MileageViewModel>(
                            factory = com.example.simpleexpenses.ui.MileageVMFactory(context, db.mileageDao())
                        )
                        com.example.simpleexpenses.ui.SettingsScreen(
                            mileageVM = mvm,
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}