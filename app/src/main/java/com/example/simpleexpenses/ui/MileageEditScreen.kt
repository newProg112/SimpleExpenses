package com.example.simpleexpenses.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.health.connect.datatypes.ExerciseRoute
import android.location.Location
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.simpleexpenses.data.MileageClaim
import com.example.simpleexpenses.data.MileageEntry
import com.example.simpleexpenses.data.VehicleType
import com.example.simpleexpenses.network.CrowFliesRoutesRepository
import com.example.simpleexpenses.network.LatLng
import kotlinx.coroutines.launch
import java.io.File
import java.lang.ProcessBuilder.Redirect.to
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Intent to open the attached receipt in an external viewer
    val openReceipt: (String) -> Unit = { uriStr ->
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(uriStr), "*/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
        }
    }

    var passengersText by remember { mutableStateOf("") }

    // Local UI-only fields
    // Keep From/To for user context; we fold them into note on save.
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var showVehicleMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var milesTouched by remember { mutableStateOf(false) }
    var dateTouched by remember { mutableStateOf(false) }

    // Validation flags
    var dateIsValid by remember { mutableStateOf(true) }
    var passengersTouched by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val uri = saveBitmap(context, bitmap)
            vm.onReceiptSelected(uri.toString())
        }
    }

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

            if (editId != null) {
                vm.saveClaim(editId, from, to)
                // Toast.makeText(context, "Receipt attached", Toast.LENGTH_SHORT).show()
                // show Snackbar instead of Toast
                scope.launch {
                    snackbarHostState.showSnackbar("Receipt attached")
                }
            }
        }
    }

    // Date <-> epoch helpers
    fun epochToLocalDate(epoch: Long): LocalDate =
        Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDate()

    fun localDateToEpoch(ld: LocalDate): Long =
        ld.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // --- UK strict date parsing: dd/MM/yyyy ---
    val ukDateFormatter = remember {
        java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT)
    }

    fun localDateToUkText(ld: LocalDate): String =
        ld.format(ukDateFormatter)

    fun parseUkDateOrNull(text: String): LocalDate? =
        try {
            LocalDate.parse(text.trim(), ukDateFormatter)
        } catch (_: Exception) {
            null
        }

    // Derived display currency – use HMRC tiered logic if available
    val currency = remember { NumberFormat.getCurrencyInstance() }

    // Figure out the estimated reimbursement in pence
    val estimatedPence: Int = ui.settings?.let { s ->
        if (s.useHmrc && ui.vehicle == VehicleType.CAR) {
            val miles = ui.miles.coerceAtLeast(0.0)
            val thresholdMiles = s.hmrcThresholdMiles.toDouble()

            // First band: up to threshold at first rate
            val firstBandMiles = miles.coerceAtMost(thresholdMiles)
            // Second band: anything above threshold at second rate
            val secondBandMiles = (miles - thresholdMiles).coerceAtLeast(0.0)

            val firstPart = firstBandMiles * s.hmrcFirstRatePence
            val secondPart = secondBandMiles * s.hmrcSecondRatePence

            (firstPart + secondPart).roundToInt()
        } else {
            // Not using HMRC or not a car – fall back to whatever the VM computed
            ui.liveCostPence
        }
    } ?: ui.liveCostPence

    val poundsString = currency.format(estimatedPence / 100.0)

    if (editId != null) {
        val existing by vm.entry(editId).collectAsState(initial = null)
        LaunchedEffect(existing?.id) {
            existing?.let { e ->
                // Set date into VM (needs onDateChanged in the VM — we added this earlier)
                val epoch = e.date.atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
                vm.onDateChanged(epoch)

                // Translate distanceMeters -> miles for HMRC calc, rounded to 1 decimal
                val miles = (e.distanceMeters / 1609.344 * 10.0).roundToInt() / 10.0
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (editId != null) "Edit mileage" else "Add mileage") },
                actions = {
                    if (editId != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) { Text("Delete") }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDone,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    val canSave = ui.miles > 0 && dateIsValid

                    Button(
                        onClick = {
                            var hasError = false

                            // Distance must be > 0
                            if (ui.miles <= 0.0) {
                                milesTouched = true
                                hasError = true
                            }

                            // Date must be valid
                            if (!dateIsValid) {
                                dateTouched = true
                                hasError = true
                            }

                            if (hasError) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Please fix the highlighted fields before saving"
                                    )
                                }
                                return@Button
                            }

                            // Build route suffix from From/To
                            val suffix = buildString {
                                if (from.isNotBlank() || to.isNotBlank()) {
                                    append("Route: ")
                                    append(if (from.isNotBlank()) from else "?")
                                    append(" → ")
                                    append(if (to.isNotBlank()) to else "?")
                                }
                            }

                            val baseNote = note.trim()

                            val finalNote = if (suffix.isBlank()) {
                                baseNote
                            } else {
                                // If note already includes this exact route, don't add it again
                                if (baseNote.contains(suffix)) {
                                    baseNote
                                } else if (baseNote.isBlank()) {
                                    suffix
                                } else {
                                    "$baseNote — $suffix"
                                }
                            }

                            vm.onNoteChanged(finalNote)
                            vm.saveClaim(editId, from, to)
                            Toast
                                .makeText(
                                    context,
                                    "Mileage saved",
                                    Toast.LENGTH_SHORT
                                )
                                .show()
                            onDone()
                        },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    ) { pad ->
        val scrollState = rememberScrollState()

        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .verticalScroll(scrollState)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Date
            var dateText by remember(ui.dateEpochMillis) {
                mutableStateOf(localDateToUkText(epochToLocalDate(ui.dateEpochMillis)))
            }

            // Show error only after user has touched the field
            val dateError = dateTouched && !dateIsValid

            LaunchedEffect(ui.passengers) {
                passengersText = if (ui.passengers <= 0) "" else ui.passengers.toString()
            }

            OutlinedTextField(
                value = dateText,
                onValueChange = { text ->
                    dateText = text
                    dateTouched = true

                    val parsed = parseUkDateOrNull(text)
                    if (parsed != null) {
                        dateIsValid = true
                        vm.onDateChanged(localDateToEpoch(parsed))
                    } else {
                        dateIsValid = false
                    }
                },
                label = { Text("Date (dd/MM/yyyy)") },
                isError = dateError,
                supportingText = {
                    if (dateError) {
                        Text("Use dd/MM/yyyy, e.g. 17/12/2025")
                    }
                },
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
            val milesError = milesTouched && ui.miles <= 0.0

            OutlinedTextField(
                value = if (ui.miles == 0.0) "" else String.format("%.1f", ui.miles),
                onValueChange = { text ->
                    milesTouched = true

                    val miles = text.trim().replace(",", ".").toDoubleOrNull() ?: 0.0
                    val capped = miles.coerceIn(0.0, 500.0)

                    vm.onMilesChanged(capped)
                },
                label = { Text("Distance (miles)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = milesError,
                supportingText = {
                    if (milesError) {
                        Text("Enter a distance greater than zero, e.g. 3.5")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            // Passengers
            val passengersParsed = passengersText.toIntOrNull()
            val passengersError = passengersTouched && passengersText.isNotBlank() && passengersParsed == null

            OutlinedTextField(
                value = passengersText,
                onValueChange = { t ->
                    passengersTouched = true
                    passengersText = t

                    val parsed = t.toIntOrNull()

                    // Only update VM when parse is valid or blank
                    if (t.isBlank()) vm.onPassengersChanged(0)
                    else parsed?.let { vm.onPassengersChanged(it.coerceAtLeast(0)) }
                },
                label = { Text("Passengers") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = passengersError,
                supportingText = {
                    if (passengersError) Text("Enter a whole number (or leave blank)")
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
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

            if (ui.receiptUri != null) {
                val uri = Uri.parse(ui.receiptUri)
                val context = LocalContext.current

                // Detect PDF using MIME type OR file extension as a fallback
                val isPdf = remember(uri) {
                    val type = context.contentResolver.getType(uri)
                    type == "application/pdf" ||
                            ui.receiptUri?.lowercase()?.endsWith(".pdf") == true
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(Modifier.padding(8.dp)) {

                        Text("Attachment", style = MaterialTheme.typography.titleMedium)

                        if (isPdf) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .clickable { openFile(context, uri) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.PictureAsPdf,
                                    contentDescription = "PDF receipt"
                                )
                                Text(
                                    text = "Open PDF",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        } else {
                            AsyncImage(
                                model = uri,
                                contentDescription = "Receipt image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }

                        TextButton(onClick = { vm.onReceiptCleared() }) {
                            Text("Remove attachment")
                        }
                    }
                }
            }

            // Live total / HMRC info
            val hmrcMode = ui.settings?.useHmrc == true && ui.vehicle == VehicleType.CAR

            if (hmrcMode) {
                Text(
                    text = "Reimbursement will be calculated using the HMRC 45p / 25p split when you save.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Estimated reimbursement: $poundsString",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = { pickReceipt.launch(arrayOf("image/*", "application/pdf")) }) {
                    Text(if (ui.hasReceipt) "Replace receipt" else "Attach receipt")
                }

                Button(onClick = { cameraLauncher.launch(null) }) {
                    Text("Take photo")
                }

                if (ui.hasReceipt && ui.receiptUri != null) {
                    OutlinedButton(onClick = { openReceipt(ui.receiptUri!!) }) {
                        Text("View receipt")
                    }
                }
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

fun saveBitmap(context: Context, bitmap: Bitmap): Uri {
    val filename = "mileage_${System.currentTimeMillis()}.jpg"
    val out = context.openFileOutput(filename, Context.MODE_PRIVATE)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
    out.close()
    return File(context.filesDir, filename).toUri()
}

private fun openFile(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
