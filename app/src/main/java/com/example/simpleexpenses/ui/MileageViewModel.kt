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

class MileageViewModel(
    private val dao: MileageDao,
    private val settingsRepo: SettingsRepository
) : ViewModel() {

    val items = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<MileageRateSettings?> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Lazily, null)

    // You may already have MutableStateFlow<MileageEditState>; just ensure we recompute on change
    private val _ui = kotlinx.coroutines.flow.MutableStateFlow(MileageEditState())
    val ui: StateFlow<MileageEditState> = _ui

    private var editingId: Long? = null

    init {
        // When settings emit (including the first time), recompute with current inputs
        viewModelScope.launch {
            settingsRepo.settings.collect { s ->
                val st = _ui.value
                val cost = com.example.simpleexpenses.domain.MileageCalculator
                    .computeCostPence(st.miles, st.vehicle, st.passengers, s)
                _ui.value = st.copy(settings = s, liveCostPence = cost)
            }
        }
    }

    fun onMilesChanged(m: Double) = recompute(_ui.value.copy(miles = m))
    fun onPassengersChanged(p: Int) = recompute(_ui.value.copy(passengers = p))
    fun onVehicleChanged(v: VehicleType) = recompute(_ui.value.copy(vehicle = v))
    fun onNoteChanged(n: String) { _ui.value = _ui.value.copy(note = n) }

    fun loadForEdit(existing: MileageClaim?) {
        if (existing == null) return
        _ui.value = _ui.value.copy(
            dateEpochMillis = existing.dateEpochMillis,
            miles = existing.miles,
            note = existing.note.orEmpty(),
            vehicle = existing.vehicleType,
            passengers = existing.passengers,
            receiptUri = existing.receiptUri,
            hasReceipt = existing.hasReceipt
        )
        recompute(_ui.value)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun beginEdit(id: Long?) = viewModelScope.launch {
        editingId = id
        if (id != null) {
            dao.observeById(id).firstOrNull()?.let { e ->
                _ui.value = _ui.value.copy(
                    dateEpochMillis = e.date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    miles = (e.distanceMeters / 1609.344),
                    note = e.notes.orElse(""),
                    receiptUri = e.receiptUri,
                    hasReceipt = e.hasReceipt
                )
                recompute(_ui.value)
            }
        }
    }
    private fun String?.orElse(fallback: String) = this ?: fallback

    private fun recompute(next: MileageEditState) {
        val s = settings.value
        val cost = if (s != null)
            MileageCalculator.computeCostPence(next.miles, next.vehicle, next.passengers, s)
        else 0
        _ui.value = next.copy(liveCostPence = cost, settings = s)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveClaim(editId: Long? = null, from: String = "", to: String = "") =
        viewModelScope.launch {
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

            val st = _ui.value
            val cost = MileageCalculator.computeCostPence(st.miles, st.vehicle, st.passengers, s)
            val date = Instant.ofEpochMilli(st.dateEpochMillis)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val distanceMeters = (st.miles * 1609.344).roundToInt()
            val rateForEntry =
                if (s.useHmrc && st.vehicle == VehicleType.CAR) s.hmrcFirstRatePence else s.customRatePence

            // Critical: prefer the explicit editId (from screen) then the remembered editingId
            val targetId: Long? = editId ?: editingId

            val entry = MileageEntry(
                id = targetId ?: 0L, // 0L => insert; otherwise update
                date = date,
                fromLabel = if (from.isBlank()) "?" else from,
                toLabel   = if (to.isBlank()) "?" else to,
                distanceMeters = distanceMeters,
                ratePencePerMile = rateForEntry,
                amountPence = cost,
                notes = st.note.ifBlank { null },
                receiptUri = st.receiptUri,
                hasReceipt = st.hasReceipt
            )

            // Your @Upsert may return Unit; that’s fine—we still wrote the row
            dao.upsert(entry)

            // If we just inserted (targetId == null), remember that future saves should update.
            // We can’t know the new id here if @Upsert returns Unit, so we’ll rely on the
            // edit route passing id for edits; this path mainly matters on new rows.
            if (targetId != null) editingId = targetId

            android.util.Log.d(
                "MileageSave",
                "Saved id=${targetId ?: 0L} date=$date miles=${st.miles} costP=$cost from='$from' to='$to'"
            )
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
}