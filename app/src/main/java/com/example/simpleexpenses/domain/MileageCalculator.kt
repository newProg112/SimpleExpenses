package com.example.simpleexpenses.domain

import com.example.simpleexpenses.data.VehicleType
import com.example.simpleexpenses.prefs.MileageRateSettings
import kotlin.math.max
import kotlin.math.roundToInt

object MileageCalculator {

    /**
     * Returns reimbursement **in pence** for the given miles.
     * If settings.useHmrc == true and vehicle is CAR, applies tiering:
     *  - first N miles at firstRate
     *  - remainder at secondRate
     * Otherwise uses a flat custom rate.
     *
     * Passengers uplift (optional): +5p per passenger per business mile, for cars only.
     */
    fun computeCostPence(
        miles: Double,
        vehicle: VehicleType,
        passengers: Int,
        settings: MileageRateSettings
    ): Int {
        if (miles <= 0.0) return 0

        val base =
            if (settings.useHmrc && vehicle == VehicleType.CAR) {
                val firstBandMiles = max(0.0, minOf(miles, settings.hmrcThresholdMiles.toDouble()))
                val secondBandMiles = max(0.0, miles - firstBandMiles)
                (firstBandMiles * settings.hmrcFirstRatePence +
                        secondBandMiles * settings.hmrcSecondRatePence)
            } else {
                // Flat rate path (useful for motorcycle/cycle or custom policy)
                val rate = when (vehicle) {
                    VehicleType.CAR -> settings.customRatePence
                    VehicleType.MOTORCYCLE -> 24 // common UK guidance; make configurable later
                    VehicleType.CYCLE -> 20      // common UK guidance; make configurable later
                }
                miles * rate
            }

        val passengerUpliftPencePerMile = if (vehicle == VehicleType.CAR) 5 else 0
        val uplift = miles * passengers * passengerUpliftPencePerMile

        // round to nearest penny (pence already, but miles can be fractional)
        return (base + uplift).roundToInt()
    }
}