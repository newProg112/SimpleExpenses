package com.example.simpleexpenses.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mileage_claims")
data class MileageClaim(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochMillis: Long,
    val miles: Double,
    val note: String? = null,
    val vehicleType: VehicleType = VehicleType.CAR,
    val passengers: Int = 0,

    // Persist what we actually used at save-time, for auditability/exports:
    val ratePenceApplied: Int? = null,   // null means HMRC tiered was used
    val costPence: Int = 0,               // computed reimbursement in pence

    val receiptUri: String? = null,
    val hasReceipt: Boolean = false
)

enum class VehicleType { CAR, MOTORCYCLE, CYCLE }
