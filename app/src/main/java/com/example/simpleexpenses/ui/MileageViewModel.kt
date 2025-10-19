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
    val settings: MileageRateSettings? = null
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
            passengers = existing.passengers
        )
        recompute(_ui.value)
    }

    private fun recompute(next: MileageEditState) {
        val s = settings.value
        val cost = if (s != null)
            MileageCalculator.computeCostPence(next.miles, next.vehicle, next.passengers, s)
        else 0
        _ui.value = next.copy(liveCostPence = cost, settings = s)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun saveClaim(editingId: Long? = null, from: String = "", to: String = "") = viewModelScope.launch {
        val s = settings.value ?: return@launch
        val st = _ui.value
        val cost = MileageCalculator.computeCostPence(st.miles, st.vehicle, st.passengers, s)
        val date = Instant.ofEpochMilli(st.dateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        val distanceMeters = (st.miles * 1609.344).roundToInt()

        val rateForEntry = if (s.useHmrc && st.vehicle == VehicleType.CAR) s.hmrcFirstRatePence else s.customRatePence

        val entry = MileageEntry(
            id = editingId ?: 0L,
            date = date,
            fromLabel = from.ifBlank { "?" },
            toLabel = to.ifBlank { "?" },
            distanceMeters = distanceMeters,
            ratePencePerMile = rateForEntry,
            amountPence = cost,
            notes = st.note.ifBlank { null }
        )
        dao.upsert(entry)
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
}