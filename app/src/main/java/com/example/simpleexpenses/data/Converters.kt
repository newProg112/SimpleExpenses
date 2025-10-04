package com.example.simpleexpenses.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStatus(value: ExpenseStatus?): String? = value?.name
    @TypeConverter
    fun toStatus(value: String?): ExpenseStatus? =
        value?.let { runCatching { ExpenseStatus.valueOf(it) }.getOrNull() }
}