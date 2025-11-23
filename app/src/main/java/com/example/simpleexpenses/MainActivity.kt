package com.example.simpleexpenses

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.simpleexpenses.ui.AppThemeMode
import com.example.simpleexpenses.ui.LocalApp
import com.example.simpleexpenses.ui.ExpenseViewModel
import com.example.simpleexpenses.ui.ExpenseEditScreen
import com.example.simpleexpenses.ui.ExpenseListScreen
import com.example.simpleexpenses.ui.ExpenseVMFactory
import com.example.simpleexpenses.ui.ExportScreen
import com.example.simpleexpenses.ui.MileageRoute
import com.example.simpleexpenses.ui.SimpleExpensesTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LocalApp

        setContent {
            // App-wide theme mode (for now, in-memory only)
            var themeMode by remember { mutableStateOf(AppThemeMode.SYSTEM) }

            SimpleExpensesTheme(mode = themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val vm: ExpenseViewModel = viewModel(factory = ExpenseVMFactory(app))

                    // work out which screen to start on
                    val openAddExpense = remember {
                        intent?.getBooleanExtra("open_add_expense", false) == true
                    }
                    val startRoute = if (openAddExpense) "edit" else "activity"

                    NavHost(navController = nav, startDestination = startRoute) {
                        composable("activity") {
                            val context = LocalContext.current.applicationContext
                            val db = remember { com.example.simpleexpenses.data.AppDatabase.get(context) }
                            val mvm = viewModel<com.example.simpleexpenses.ui.MileageViewModel>(
                                factory = com.example.simpleexpenses.ui.MileageVMFactory(context, db.mileageDao())
                            )

                            com.example.simpleexpenses.ui.CombinedActivityScreen(
                                expenseVM = vm,
                                mileageVM = mvm,
                                onExpenseClick = { id -> nav.navigate("edit?id=$id") },
                                onMileageClick = { id -> nav.navigate("mileage_edit?id=$id") },
                                onAddExpense = { nav.navigate("edit") },
                                onAddMileage = { nav.navigate("mileage") },
                                onOpenExport = { nav.navigate("export") },
                                onOpenSettings = { nav.navigate("settings") }
                            )
                        }
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
                        ) { backStackEntry ->
                            val context = LocalContext.current.applicationContext
                            val db = remember { com.example.simpleexpenses.data.AppDatabase.get(context) }
                            val mvm = viewModel<com.example.simpleexpenses.ui.MileageViewModel>(
                                factory = com.example.simpleexpenses.ui.MileageVMFactory(context, db.mileageDao())
                            )

                            val rawId = backStackEntry.arguments?.getLong("id") ?: -1L
                            val editId: Long? = if (rawId > 0) rawId else null

                            LaunchedEffect(editId) { mvm.beginEdit(editId) }

                            com.example.simpleexpenses.ui.MileageEditScreen(
                                vm = mvm,
                                editId = editId,
                                onDone = { nav.popBackStack() }
                            )
                        }

                        composable("settings") {
                            val context = LocalContext.current.applicationContext
                            val db = remember { com.example.simpleexpenses.data.AppDatabase.get(context) }
                            val mvm = viewModel<com.example.simpleexpenses.ui.MileageViewModel>(
                                factory = com.example.simpleexpenses.ui.MileageVMFactory(context, db.mileageDao())
                            )
                            com.example.simpleexpenses.ui.SettingsScreen(
                                mileageVM = mvm,
                                themeMode = themeMode,
                                onThemeChange = { newMode -> themeMode = newMode },
                                onOpenAppInfo = { nav.navigate("app_info") },
                                onBack = { nav.popBackStack() }
                            )
                        }

                        composable("app_info") {
                            com.example.simpleexpenses.ui.AppInfoScreen(
                                onBack = { nav.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}