package com.example.simpleexpenses.notify

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

object ReminderScheduler {

    private const val DAILY_WORK_NAME = "daily_reminder"

    fun sendTestNow(workManager: WorkManager, title: String, message: String) {
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_MESSAGE, message)
            .build()

        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork("test_reminder_now", ExistingWorkPolicy.REPLACE, req)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun scheduleDaily(
        workManager: WorkManager,
        hour: Int,
        minute: Int,
        title: String,
        message: String
    ) {
        val data = Data.Builder()
            .putString(ReminderWorker.KEY_TITLE, title)
            .putString(ReminderWorker.KEY_MESSAGE, message)
            .build()

        val now = LocalDateTime.now()
        val todayAt = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val first = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
        val delay = java.time.Duration.between(now, first)

        val req = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay)
            .setInputData(data)
            .build()

        workManager.enqueueUniquePeriodicWork(
            DAILY_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun cancelAll(workManager: WorkManager) {
        workManager.cancelUniqueWork(DAILY_WORK_NAME)
    }
}