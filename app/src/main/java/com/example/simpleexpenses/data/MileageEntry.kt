package com.example.simpleexpenses.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "mileage_entries")
data class MileageEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val date: LocalDate,
    val fromLabel: String,
    val toLabel: String,
    /** store meters; UI displays miles/km */
    val distanceMeters: Int,
    /** pence per mile (e.g., 45 => £0.45/mi) */
    val ratePencePerMile: Int,
    /** cached computed amount in pence for reporting/export */
    val amountPence: Int,
    val notes: String? = null
)