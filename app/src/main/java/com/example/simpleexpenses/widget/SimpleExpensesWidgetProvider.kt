package com.example.simpleexpenses.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.example.simpleexpenses.MainActivity
import com.example.simpleexpenses.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SimpleExpensesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_simple_expenses)

            // Friendly “today” label
            val dateText = SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date())
            views.setTextViewText(R.id.widget_date, dateText)

            // Read latest stats from SharedPreferences
            val prefs = context.getSharedPreferences("simple_expenses_widget", Context.MODE_PRIVATE)
            val combined = prefs.getString("widget_combined_total", null)
            val missing = prefs.getInt("widget_missing_receipts", 0)
            val mileage = prefs.getString("widget_mileage_total", null)

            val statsText = when {
                combined == null -> "No data yet"
                missing > 0      -> "This month: $combined • $missing missing"
                else             -> "This month: $combined"
            }

            val mileageText = when (mileage) {
                null -> "Mileage: --"
                else -> "Mileage: $mileage"
            }

            views.setTextViewText(R.id.widget_stats, statsText)
            views.setTextViewText(R.id.widget_mileage_stats, mileageText)

            // ---- Normal open app intent (root + mileage) ----
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                pendingFlags
            )

            // Root opens app normally
            views.setOnClickPendingIntent(R.id.widget_root, openAppPending)

            // Mileage button also opens app normally for now
            views.setOnClickPendingIntent(R.id.widget_add_mileage, openAppPending)

            // ---- Quick Add Expense (auto-launch camera) ----
            val quickAddIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("open_add_expense", true)
                putExtra("open_add_expense_camera", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val quickAddPending = PendingIntent.getActivity(
                context,
                1, // use a different requestCode
                quickAddIntent,
                pendingFlags
            )

            views.setOnClickPendingIntent(R.id.widget_add_expense, quickAddPending)

            // Apply updates
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        fun forceUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, SimpleExpensesWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                SimpleExpensesWidgetProvider().onUpdate(context, manager, ids)
            }
        }
    }
}