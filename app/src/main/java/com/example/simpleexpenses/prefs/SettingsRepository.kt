package com.example.simpleexpenses.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val useHmrc = booleanPreferencesKey("use_hmrc_rates")
        val hmrcThresholdMiles = intPreferencesKey("hmrc_threshold_miles")
        val hmrcFirstRatePence = intPreferencesKey("hmrc_first_rate_pence")
        val hmrcSecondRatePence = intPreferencesKey("hmrc_second_rate_pence")
        val customRatePence = intPreferencesKey("custom_rate_pence")
    }

    // sensible UK defaults
    val settings = context.dataStore.data.map { p ->
        MileageRateSettings(
            useHmrc = p[Keys.useHmrc] ?: true,
            hmrcThresholdMiles = p[Keys.hmrcThresholdMiles] ?: 10_000,
            hmrcFirstRatePence = p[Keys.hmrcFirstRatePence] ?: 45,
            hmrcSecondRatePence = p[Keys.hmrcSecondRatePence] ?: 25,
            customRatePence = p[Keys.customRatePence] ?: 45
        )
    }

    suspend fun setUseHmrc(use: Boolean) = context.dataStore.edit { it[Keys.useHmrc] = use }
    suspend fun setCustomRatePence(p: Int) = context.dataStore.edit { it[Keys.customRatePence] = p }
    // Expose setters for the others if you want them editable.
}

data class MileageRateSettings(
    val useHmrc: Boolean,
    val hmrcThresholdMiles: Int,
    val hmrcFirstRatePence: Int,
    val hmrcSecondRatePence: Int,
    val customRatePence: Int
)