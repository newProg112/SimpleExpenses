package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleexpenses.data.MileageClaim
import com.example.simpleexpenses.data.MileageDao
import com.example.simpleexpenses.data.MileageEntry
import com.example.simpleexpenses.data.VehicleType
import com.example.simpleexpenses.domain.MileageCalculator
import com.example.simpleexpenses.prefs.MileageRateSettings
import com.example.simpleexpenses.prefs.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.O)
private fun hmrcTaxYearRange(date: LocalDate): Pair<LocalDate, LocalDate> {
    val year = date.year
    val taxYearStart =
        if (date.isBefore(LocalDate.of(year, 4, 6)))
            LocalDate.of(year - 1, 4, 6)
        else
            LocalDate.of(year, 4, 6)

    val taxYearEnd = taxYearStart.plusYears(1).minusDays(1)
    return taxYearStart to taxYearEnd
}

private fun metersToMiles(meters: Int): Double =
    meters / 1609.344

data class MileageEditState(
    val dateEpochMillis: Long = System.currentTimeMillis(),
    val miles: Double = 0.0,
    val note: String = "",
    val vehicle: VehicleType = VehicleType.CAR,
    val passengers: Int = 0,
    val liveCostPence: Int = 0,
    val settings: MileageRateSettings? = null,
    val receiptUri: String? = null,
    val hasReceipt: Boolean = false
)

@RequiresApi(Build.VERSION_CODES.O)
class MileageViewModel(
    private val dao: MileageDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val items = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<MileageRateSettings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // You may already have MutableStateFlow<MileageEditState>; just ensure we recompute on change
    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(MileageEditState())
    val ui: StateFlow<MileageEditState> = _ui

    private var editingId: Long? = null

    init {
        viewModelScope.launch {
            settingsRepo.settings.collect {
                // Whenever settings change, just recompute using the latest UI state.
                val st = _ui.value
                recompute(st)
            }
        }
    }

    fun onMilesChanged(m: Double) {
        val next = _ui.value.copy(miles = m)
        viewModelScope.launch { recompute(next) }
    }

    fun onPassengersChanged(p: Int) {
        val next = _ui.value.copy(passengers = p)
        viewModelScope.launch { recompute(next) }
    }

    fun onVehicleChanged(v: VehicleType) {
        val next = _ui.value.copy(vehicle = v)
        viewModelScope.launch { recompute(next) }
    }

    fun onNoteChanged(n: String) { _ui.value = _ui.value.copy(note = n) }

    fun loadForEdit(existing: MileageClaim?) {
        if (existing == null) return
        val next = _ui.value.copy(
            dateEpochMillis = existing.dateEpochMillis,
            miles = existing.miles,
            note = existing.note.orEmpty(),
            vehicle = existing.vehicleType,
            passengers = existing.passengers,
            receiptUri = existing.receiptUri,
            hasReceipt = existing.hasReceipt
        )
        viewModelScope.launch { recompute(next) }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun beginEdit(id: Long?) = viewModelScope.launch {
        editingId = id

        if (id != null) {
            dao.observeById(id).firstOrNull()?.let { e ->
                val next = _ui.value.copy(
                    dateEpochMillis = e.date
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli(),
                    miles = (e.distanceMeters / 1609.344),
                    note = e.notes.orElse(""),
                    receiptUri = e.receiptUri,
                    hasReceipt = e.hasReceipt
                )
                recompute(next)
            }
        } else {
            // NEW CLAIM: reset draft state
            val next = MileageEditState(
                dateEpochMillis = System.currentTimeMillis(),
                miles = 0.0,
                note = "",
                vehicle = VehicleType.CAR,
                passengers = 0,
                receiptUri = null,
                hasReceipt = false
            )
            recompute(next)
        }
    }

    private fun String?.orElse(fallback: String) = this ?: fallback

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun recompute(next: MileageEditState) {
        // Use live settings if present, otherwise safe HMRC defaults (same as saveClaim)
        val s = settings.value ?: MileageRateSettings(
            useHmrc = true,
            hmrcThresholdMiles = 10_000,
            hmrcFirstRatePence = 45,
            hmrcSecondRatePence = 25,
            customRatePence = 45,
            reminderEnabled = false,
            reminderHour = 19,
            reminderMinute = 0
        )

        val useHmrcForThis = s.useHmrc && next.vehicle == VehicleType.CAR

        val cost: Int = if (!useHmrcForThis) {
            // Non-HMRC or non-car → just use the standard calculator
            MileageCalculator.computeCostPence(next.miles, next.vehicle, next.passengers, s)
        } else {
            // HMRC + CAR → use YEAR-TO-DATE logic, same as saveClaim

            // Work out tax year containing this date
            val date = Instant.ofEpochMilli(next.dateEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val (taxYearStart, taxYearEnd) = hmrcTaxYearRange(date)

            // Total mileage already claimed this tax year (in meters, then miles)
            val usedMeters = dao.totalDistanceMetersInRange(taxYearStart, taxYearEnd)
            val usedMiles = metersToMiles(usedMeters)

            val threshold = s.hmrcThresholdMiles.toDouble()

            // Remaining miles at 45p before hitting 10,000 for the year
            val remainingAt45 = (threshold - usedMiles).coerceAtLeast(0.0)

            // For THIS claim:
            val milesAt45 = remainingAt45.coerceIn(0.0, next.miles)
            val milesAt25 = (next.miles - milesAt45).coerceAtLeast(0.0)

            if (milesAt25 <= 0.0) {
                // Still under 10,000 for the year → all at 45p
                (next.miles * s.hmrcFirstRatePence).roundToInt()
            } else {
                // We cross the threshold in this claim → split at 45p / 25p
                (milesAt45 * s.hmrcFirstRatePence).roundToInt() +
                        (milesAt25 * s.hmrcSecondRatePence).roundToInt()
            }
        }

        _ui.value = next.copy(
            liveCostPence = cost,
            settings = s
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveClaim(editId: Long? = null, from: String = "", to: String = "") =
        viewModelScope.launch {
            // Basic validation – don't save empty claims
            val st = _ui.value
            if (st.miles <= 0.0) {
                android.util.Log.d("MileageSave", "Refusing to save: miles <= 0")
                return@launch
            }

            // Use live settings if present, otherwise safe defaults so we still save
            val s = settings.value ?: MileageRateSettings(
                useHmrc = true,
                hmrcThresholdMiles = 10_000,
                hmrcFirstRatePence = 45,
                hmrcSecondRatePence = 25,
                customRatePence = 45,
                reminderEnabled = false,
                reminderHour = 19,
                reminderMinute = 0
            )

            val date = Instant.ofEpochMilli(st.dateEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()

            // ---- HMRC auto-split logic (cars only, when HMRC mode is on) ----
            val useHmrcForThis =
                s.useHmrc && st.vehicle == VehicleType.CAR

            val (taxYearStart, taxYearEnd) = hmrcTaxYearRange(date)

            // This is a suspend DAO call, so it MUST be inside this launch { } block
            val usedMeters = dao.totalDistanceMetersInRange(taxYearStart, taxYearEnd)
            val usedMiles = metersToMiles(usedMeters)

            val threshold = s.hmrcThresholdMiles.toDouble()

            // How many miles at 45p are still available in this tax year?
            val remainingAt45 = (threshold - usedMiles).coerceAtLeast(0.0)

            // Miles at first rate (45p) – can't exceed this trip's miles
            val milesAt45 = if (useHmrcForThis) {
                remainingAt45.coerceIn(0.0, st.miles)
            } else {
                0.0
            }

            // Miles at second rate (25p) – whatever's left
            val milesAt25 = if (useHmrcForThis) {
                (st.miles - milesAt45).coerceAtLeast(0.0)
            } else {
                0.0
            }

            // If not using HMRC or not CAR, treat everything at the custom rate
            val useSingleCustomRate = !useHmrcForThis

            fun passengersBonusPence(miles: Double): Int {
                // If your MileageCalculator already handles passengers, you can just
                // call that instead; for now we assume the rate is per-mile only.
                return 0
            }

            // Helper to save one entry
            suspend fun saveEntry(
                miles: Double,
                rate: Int,
                noteSuffix: String?
            ) {
                if (miles <= 0.0) return

                val meters = (miles * 1609.344).roundToInt()
                val amount = (miles * rate).roundToInt() + passengersBonusPence(miles)

                dao.upsert(
                    MileageEntry(
                        id = 0L, // always insert for auto-split claims
                        date = date,
                        fromLabel = if (from.isBlank()) "?" else from,
                        toLabel = if (to.isBlank()) "?" else to,
                        distanceMeters = meters,
                        ratePencePerMile = rate,
                        amountPence = amount,
                        notes = st.note
                            .takeIf { it.isNotBlank() }
                            ?.let { base -> if (noteSuffix != null) base + noteSuffix else base },
                        receiptUri = st.receiptUri,
                        hasReceipt = st.hasReceipt
                    )
                )
            }

            if (useSingleCustomRate) {
                // Everything at custom rate (no HMRC split)
                saveEntry(
                    miles = st.miles,
                    rate = s.customRatePence,
                    noteSuffix = null
                )
            } else {
                if (milesAt25 == 0.0) {
                    // We haven't crossed 10,000 miles in this tax year yet – single 45p entry
                    saveEntry(
                        miles = st.miles,
                        rate = s.hmrcFirstRatePence,
                        noteSuffix = null
                    )
                } else {
                    // Crosses the threshold → auto-split into two entries
                    saveEntry(
                        miles = milesAt45,
                        rate = s.hmrcFirstRatePence,
                        noteSuffix = " (HMRC split: up to 10,000 miles)"
                    )
                    saveEntry(
                        miles = milesAt25,
                        rate = s.hmrcSecondRatePence,
                        noteSuffix = " (HMRC split: over 10,000 miles)"
                    )
                }
            }

            android.util.Log.d(
                "MileageSave",
                "Saved HMRC auto-split claim for date=$date totalMiles=${st.miles} from='$from' to '$to'"
            )
        }

    @RequiresApi(Build.VERSION_CODES.O)
    fun duplicateClaim(id: Long) = viewModelScope.launch {
        // Load the existing entry by id
        val existing = dao.observeById(id).firstOrNull() ?: return@launch

        // Create a new entry based on the existing one, but:
        // - id = 0L so Room inserts a new row
        // - date = today
        // - clear any receipt (user can attach a fresh one)
        val newEntry = existing.copy(
            id = 0L,
            date = java.time.LocalDate.now(),
            receiptUri = null,
            hasReceipt = false
        )

        dao.upsert(newEntry)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun totalPenceInMonth(year: Int, month: Int) =
        dao.observeTotalPenceInRange(
            LocalDate.of(year, month, 1),
            LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
        )

    fun save(
        id: Long? = null,
        date: LocalDate,
        fromLabel: String,
        toLabel: String,
        distanceMeters: Int,
        ratePencePerMile: Int,
        notes: String?
    ) {
        val miles = distanceMeters / 1609.344
        val amountPence = (miles * ratePencePerMile).roundToInt()
        val entry = MileageEntry(
            id = id ?: 0L,
            date = date,
            fromLabel = fromLabel.trim(),
            toLabel = toLabel.trim(),
            distanceMeters = distanceMeters,
            ratePencePerMile = ratePencePerMile,
            amountPence = amountPence,
            notes = notes?.ifBlank { null }
        )
        viewModelScope.launch { dao.upsert(entry) }
    }

    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }

    fun entry(id: Long) = dao.observeById(id)

    fun onDateChanged(epochMillis: Long) {
        _ui.value = _ui.value.copy(dateEpochMillis = epochMillis)
    }

    fun setUseHmrc(b: Boolean) = viewModelScope.launch { settingsRepo.setUseHmrc(b) }
    fun setCustomRatePence(p: Int) = viewModelScope.launch { settingsRepo.setCustomRatePence(p) }
    fun setReminderEnabled(b: Boolean) = viewModelScope.launch { settingsRepo.setReminderEnabled(b) }
    fun setReminderTime(h: Int, m: Int) = viewModelScope.launch { settingsRepo.setReminderTime(h, m) }

    fun onReceiptSelected(uri: String?) {
        _ui.value = _ui.value.copy(
            receiptUri = uri,
            hasReceipt = !uri.isNullOrBlank()
        )
    }

    fun onReceiptCleared() {
        onReceiptSelected(null)
    }
}