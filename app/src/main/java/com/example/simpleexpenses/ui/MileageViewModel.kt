package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.simpleexpenses.data.MileageDao
import com.example.simpleexpenses.data.MileageEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class MileageViewModel(
    private val dao: MileageDao
) : ViewModel() {

    val items = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
}