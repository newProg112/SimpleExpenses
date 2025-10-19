package com.example.simpleexpenses.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.simpleexpenses.MainActivity
import com.example.simpleexpenses.R
import com.example.simpleexpenses.ui.LocalApp

class ReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        // Tap opens app
        val tapIntent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            1001,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = inputData.getString(KEY_TITLE) ?: "Simple Expenses"
        val text  = inputData.getString(KEY_MESSAGE) ?: "Log today’s expenses/mileage."

        val notif = NotificationCompat.Builder(context, LocalApp.REMINDER_CHANNEL_ID)
            // If you don’t have a status icon, this falls back to the app icon
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        // Guards
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.success()
        }

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notif)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val NOTIF_ID = 7001
    }
}