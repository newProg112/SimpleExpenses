package com.example.simpleexpenses.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun fromStatus(s: ExpenseStatus): String = s.name
    @TypeConverter
    fun toStatus(s: String): ExpenseStatus = ExpenseStatus.valueOf(s)
}