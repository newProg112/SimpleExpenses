package com.example.simpleexpenses

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.example.simpleexpenses.ui.ExportScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as LocalApp

        setContent {
            Surface(color = MaterialTheme.colorScheme.background) {
                val nav = rememberNavController()
                val vm: ExpenseViewModel = viewModel(factory = app.viewModelFactory)

                NavHost(navController = nav, startDestination = "list") {
                    composable("list") {
                        ExpenseListScreen(
                            viewModel = vm,
                            onAdd = { nav.navigate("edit") },
                            onEdit = { id -> nav.navigate("edit?id=$id") },
                            onExport = { nav.navigate("export") }
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
                }
            }
        }
    }
}