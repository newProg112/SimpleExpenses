package com.example.simpleexpenses.ui

import android.content.Intent
import android.health.connect.datatypes.ExerciseRoute
import android.location.Location
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.simpleexpenses.data.MileageClaim
import com.example.simpleexpenses.data.MileageEntry
import com.example.simpleexpenses.data.VehicleType
import com.example.simpleexpenses.network.CrowFliesRoutesRepository
import com.example.simpleexpenses.network.LatLng
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MileageEditScreen(
    vm: MileageViewModel,
    onDone: () -> Unit,
    editId: Long? = null
) {
    // Observe VM UI/state
    val ui by vm.ui.collectAsState()

    val context = LocalContext.current

    // System picker for image/PDF receipt
    val pickReceipt = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* some providers don’t support persist */ }
            vm.onReceiptSelected(it.toString())
        }
    }

    // Intent to open the attached receipt in an external viewer
    val openReceipt: (String) -> Unit = { uriStr ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uriStr), "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }


    // Local UI-only fields
    // Keep From/To for user context; we fold them into note on save.
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showVehicleMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Date <-> epoch helpers
    fun epochToLocalDate(epoch: Long): LocalDate =
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()

    fun localDateToEpoch(ld: LocalDate): Long =
        ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // Derived display currency from vm.liveCostPence
    val currency = remember { NumberFormat.getCurrencyInstance() }
    val poundsString = currency.format(ui.liveCostPence / 100.0)

    val scope = rememberCoroutineScope()

    if (editId != null) {
        val existing by vm.entry(editId).collectAsState(initial = null)
        LaunchedEffect(existing?.id) {
            existing?.let { e ->
                // Set date into VM (needs onDateChanged in the VM — we added this earlier)
                val epoch = e.date.atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                vm.onDateChanged(epoch)

                // Translate distanceMeters -> miles for HMRC calc
                val miles = e.distanceMeters / 1609.344
                vm.onMilesChanged(miles)

                // Notes
                vm.onNoteChanged(e.notes.orEmpty())

                // Keep From/To in the local screen fields for context (will be appended to note on save)
                from = e.fromLabel
                to = e.toLabel
                note = e.notes.orEmpty()
            }
        }
    }

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
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date
            var dateText by remember(ui.dateEpochMillis) {
                mutableStateOf(epochToLocalDate(ui.dateEpochMillis).toString())
            }
            OutlinedTextField(
                value = dateText,
                onValueChange = {
                    dateText = it
                    runCatching { LocalDate.parse(it) }
                        .onSuccess { vm.onDateChanged(localDateToEpoch(it)) }
                },
                label = { Text("Date (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            // From / To (kept as descriptive labels; saved into note)
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("From (optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Miles (drives live HMRC calc via VM)
            OutlinedTextField(
                value = if (ui.miles == 0.0) "" else ui.miles.toString(),
                onValueChange = { text ->
                    val miles = text.toDoubleOrNull() ?: 0.0
                    vm.onMilesChanged(miles)
                },
                label = { Text("Distance (miles)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Passengers
            OutlinedTextField(
                value = ui.passengers.toString(),
                onValueChange = { t -> vm.onPassengersChanged(t.toIntOrNull() ?: 0) },
                label = { Text("Passengers") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Vehicle selector (Dropdown)
            Column {
                OutlinedTextField(
                    value = ui.vehicle.name,
                    onValueChange = {},
                    label = { Text("Vehicle") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showVehicleMenu = true },
                    readOnly = true
                )
                DropdownMenu(
                    expanded = showVehicleMenu,
                    onDismissRequest = { showVehicleMenu = false }
                ) {
                    VehicleType.entries.forEach { v ->
                        DropdownMenuItem(
                            text = { Text(v.name) },
                            onClick = {
                                vm.onVehicleChanged(v)
                                showVehicleMenu = false
                            }
                        )
                    }
                }
            }

            // Additional notes (user free text). We’ll append From/To on save if provided.
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    vm.onNoteChanged(it)
                },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            // Live total from HMRC calc
            Text(
                text = "Estimated reimbursement: $poundsString",
                style = MaterialTheme.typography.titleMedium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { pickReceipt.launch(arrayOf("image/*", "application/pdf")) }) {
                    Text(if (ui.hasReceipt) "Replace receipt" else "Attach receipt")
                }
                if (ui.hasReceipt && ui.receiptUri != null) {
                    OutlinedButton(onClick = { openReceipt(ui.receiptUri!!) }) {
                        Text("View receipt")
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        // Fold From/To into note if provided
                        val suffix = buildString {
                            if (from.isNotBlank() || to.isNotBlank()) {
                                append("Route: ")
                                append(if (from.isNotBlank()) from else "?")
                                append(" → ")
                                append(if (to.isNotBlank()) to else "?")
                            }
                        }
                        val finalNote =
                            listOf(note.trim(), suffix.trim())
                                .filter { it.isNotEmpty() }
                                .joinToString(" — ")

                        vm.onNoteChanged(finalNote)
                        vm.saveClaim(editId, from, to)
                        android.widget.Toast
                            .makeText(context, "Mileage saved", android.widget.Toast.LENGTH_SHORT)
                            .show()
                        onDone()
                    },
                    enabled = ui.miles > 0.0
                ) { Text("Save") }
            }

            // Delete dialog (delegates to old DAO delete if you still keep it; otherwise remove)
            if (showDeleteConfirm && editId != null) {
                AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete mileage") },
                    text = { Text("Are you sure you want to delete this mileage entry?") },
                    confirmButton = {
                        TextButton(onClick = {
                            // If you have a delete by claim id in your DAO, call it here.
                            // vm.delete(editId) // <- only if your VM exposes it for MileageClaim
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