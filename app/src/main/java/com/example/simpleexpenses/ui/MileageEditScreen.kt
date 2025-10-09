package com.example.simpleexpenses.ui

import android.health.connect.datatypes.ExerciseRoute
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.network.CrowFliesRoutesRepository
import com.example.simpleexpenses.network.LatLng
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MileageEditScreen(
    vm: MileageViewModel,
    onDone: () -> Unit,
    editId: Long? = null
) {
    var date by remember { mutableStateOf(LocalDate.now()) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var distanceMeters by remember { mutableStateOf(0) }
    var ratePencePerMile by remember { mutableStateOf(45) } // demo default
    var notes by remember { mutableStateOf("") }

    // Prefill when editing (only once when data arrives)
    if (editId != null) {
        val existing by vm.entry(editId).collectAsState(initial = null)
        LaunchedEffect(existing?.id) {
            existing?.let { e ->
                date = e.date
                from = e.fromLabel
                to = e.toLabel
                distanceMeters = e.distanceMeters
                ratePencePerMile = e.ratePencePerMile
                notes = e.notes.orEmpty()
            }
        }
    }

    val miles = (distanceMeters / 1609.344 * 10).roundToInt() / 10.0
    val amountPence = (miles * ratePencePerMile).roundToInt()
    val currency = remember { NumberFormat.getCurrencyInstance() }

    val scope = rememberCoroutineScope()
    val routesRepo = remember {
        // TODO(v2): if (DevSettings.useGoogleRoutes) GoogleRoutesRepository() else CrowFliesRoutesRepository()
        CrowFliesRoutesRepository()
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "Edit mileage" else "Add mileage") },
                actions = {
                    if (editId != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = date.toString(),
                onValueChange = { runCatching { date = LocalDate.parse(it) } },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(value = from, onValueChange = { from = it },
                label = { Text("From") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = to, onValueChange = { to = it },
                label = { Text("To") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = (if (miles.isNaN()) 0.0 else miles).toString(),
                    onValueChange = { v ->
                        val parsed = v.toDoubleOrNull() ?: 0.0
                        distanceMeters = (parsed * 1609.344).roundToInt()
                    },
                    label = { Text("Distance (miles)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = ratePencePerMile.toString(),
                    onValueChange = { ratePencePerMile = it.toIntOrNull() ?: ratePencePerMile },
                    label = { Text("Rate (p/mi)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Optional quick helper: estimate straight-line distance from two lat/lng pairs
            var fromLat by remember { mutableStateOf("") }; var fromLng by remember { mutableStateOf("") }
            var toLat by remember { mutableStateOf("") }; var toLng by remember { mutableStateOf("") }
            Text("Optional: paste coordinates to estimate distance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fromLat, { fromLat = it }, label = { Text("From lat") }, modifier = Modifier.weight(1f))
                OutlinedTextField(fromLng, { fromLng = it }, label = { Text("From lng") }, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(toLat, { toLat = it }, label = { Text("To lat") }, modifier = Modifier.weight(1f))
                OutlinedTextField(toLng, { toLng = it }, label = { Text("To lng") }, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                val aLat = fromLat.toDoubleOrNull(); val aLng = fromLng.toDoubleOrNull()
                val bLat = toLat.toDoubleOrNull();   val bLng = toLng.toDoubleOrNull()
                if (aLat != null && aLng != null && bLat != null && bLng != null) {
                    scope.launch {
                        val meters = routesRepo.computeDistanceMeters(
                            origin = LatLng(aLat, aLng),
                            dest   = LatLng(bLat, bLng)
                        )
                        distanceMeters = meters
                    }
                }
            }) { Text("Estimate from coords") }

            Text("Total: ${currency.format(amountPence / 100.0)}", style = MaterialTheme.typography.titleMedium)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        vm.save(
                            id = editId,
                            date = date,
                            fromLabel = from,
                            toLabel = to,
                            distanceMeters = distanceMeters,
                            ratePencePerMile = ratePencePerMile,
                            notes = notes.ifBlank { null }
                        )
                        onDone()
                    },
                    enabled = from.isNotBlank() && to.isNotBlank() && distanceMeters > 0
                ) { Text("Save") }

                // Confirm delete dialog
                if (showDeleteConfirm && editId != null) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Delete mileage") },
                        text = { Text("Are you sure you want to delete this mileage entry?") },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch { vm.delete(editId) }
                                showDeleteConfirm = false
                                onDone()
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}