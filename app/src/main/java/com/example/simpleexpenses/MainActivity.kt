package com.example.simpleexpenses

import android.content.Context
import android.net.Uri
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
import com.example.simpleexpenses.ui.OnboardingScreen
import com.example.simpleexpenses.ui.SimpleExpensesTheme

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LocalApp

        setContent {
            val context = LocalContext.current
            val prefs = remember {
                context.getSharedPreferences("simple_expenses_prefs", Context.MODE_PRIVATE)
            }

            // App-wide theme mode (persisted)
            var themeMode by remember {
                mutableStateOf(
                    runCatching {
                        AppThemeMode.valueOf(
                            prefs.getString("theme_mode", AppThemeMode.SYSTEM.name)
                                ?: AppThemeMode.SYSTEM.name
                        )
                    }.getOrElse { AppThemeMode.SYSTEM }
                )
            }

            val openAddExpenseCamera = remember {
                intent?.getBooleanExtra("open_add_expense_camera", false) == true
            }

            SimpleExpensesTheme(mode = themeMode) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val nav = rememberNavController()
                    val vm: ExpenseViewModel = viewModel(factory = ExpenseVMFactory(app))

                    // onboarding flag
                    var hasSeenOnboarding by remember {
                        mutableStateOf(prefs.getBoolean("onboarding_complete", false))
                    }

                    // work out which screen to start on
                    val openAddExpense = remember {
                        intent?.getBooleanExtra("open_add_expense", false) == true
                    }
                    val openAddMileage = remember {
                        intent?.getBooleanExtra("open_add_mileage", false) == true
                    }

                    val startRoute = when {
                        openAddExpense -> "edit"        // widget quick-add expense
                        openAddMileage -> "mileage"     // widget quick-add mileage
                        !hasSeenOnboarding -> "onboarding"
                        else -> "activity"
                    }

                    NavHost(navController = nav, startDestination = startRoute) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onFinished = {
                                    // mark onboarding as complete
                                    prefs.edit()
                                        .putBoolean("onboarding_complete", true)
                                        .apply()

                                    hasSeenOnboarding = true

                                    // navigate to main Activity screen and remove onboarding from back stack
                                    nav.navigate("activity") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            )
                        }

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
                                onOpenSettings = { nav.navigate("settings") },
                                onStartDraftFromCamera = { uri ->
                                    val encoded = Uri.encode(uri.toString())
                                    nav.navigate("edit?receiptUri=$encoded")
                                }
                            )
                        }
                        composable("list") {
                            ExpenseListScreen(
                                viewModel = vm,
                                onAdd = { nav.navigate("edit") },
                                onEdit = { id -> nav.navigate("edit?id=$id") },
                                onExport = { nav.navigate("export") },
                                onOpenMileage = { nav.navigate("mileage_list") },
                                onOpenSettings = { nav.navigate("settings") },
                                onOpenCombined = {
                                    nav.navigate("activity") {
                                        popUpTo("activity") { inclusive = false }
                                    }
                                }
                            )
                        }
                        composable(
                            route = "edit?id={id}&receiptUri={receiptUri}",
                            arguments = listOf(
                                navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                                navArgument("receiptUri") {
                                    type = NavType.StringType
                                    defaultValue = ""
                                    nullable = true
                                }
                            )
                        ) { backStack ->
                            val id = backStack.arguments?.getLong("id") ?: -1L
                            val receiptUriArg = backStack.arguments
                                ?.getString("receiptUri")
                                ?.takeUnless { it.isNullOrBlank() }

                            ExpenseEditScreen(
                                viewModel = vm,
                                expenseId = if (id >= 0) id else null,
                                initialReceiptUri = receiptUriArg,
                                startWithCamera = (
                                        (openAddExpenseCamera || openAddExpense) &&
                                                id < 0 &&
                                                receiptUriArg == null
                                        ),
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
                                onThemeChange = { newMode ->
                                    themeMode = newMode
                                    prefs.edit().putString("theme_mode", newMode.name).apply()
                                },
                                onOpenAppInfo = { nav.navigate("app_info") },
                                onOpenCategoryManager = { nav.navigate("categories") },
                                onShowOnboarding = {
                                    nav.navigate("onboarding")
                                },
                                onBack = { nav.popBackStack() }
                            )
                        }

                        composable("categories") {
                            val context = LocalContext.current.applicationContext
                            val db = remember { com.example.simpleexpenses.data.AppDatabase.get(context) }

                            val categoryVM = viewModel<com.example.simpleexpenses.ui.CategoryViewModel>(
                                factory = com.example.simpleexpenses.ui.CategoryVMFactory(
                                    db.expenseCategoryDao(),
                                    db.expenseDao()
                                )
                            )

                            com.example.simpleexpenses.ui.CategoryManagerScreen(
                                viewModel = categoryVM,
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