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

class SimpleExpensesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)

        appWidgetIds.forEach { appWidgetId ->
            val views = RemoteViews(context.packageName, R.layout.widget_simple_expenses)

            // Intent to open the app (Activity screen)
            val launchIntent = Intent(context, MainActivity::class.java)

            val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                pendingFlags
            )

            // Make the whole widget clickable
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            // For v1, both buttons just open the app too
            views.setOnClickPendingIntent(R.id.widget_add_expense, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_add_mileage, pendingIntent)

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