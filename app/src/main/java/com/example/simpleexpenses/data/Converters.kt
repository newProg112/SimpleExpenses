package com.example.simpleexpenses.data

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromStatus(value: ExpenseStatus?): String? = value?.name
    @TypeConverter
    fun toStatus(value: String?): ExpenseStatus? =
        value?.let { runCatching { ExpenseStatus.valueOf(it) }.getOrNull() }
    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
    @RequiresApi(Build.VERSION_CODES.O)
    @TypeConverter fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}