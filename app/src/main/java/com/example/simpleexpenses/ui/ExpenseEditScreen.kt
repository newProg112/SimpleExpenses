package com.example.simpleexpenses.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.example.simpleexpenses.data.AppDatabase
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseStatus
import com.example.simpleexpenses.ocr.ReceiptOcrHelper
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    viewModel: ExpenseViewModel,
    expenseId: Long? = null,
    initialReceiptUri: String? = null,
    startWithCamera: Boolean = false,
    onDone: () -> Unit
) {
    // Payment methods stay static for now
    val paymentMethods = listOf("Personal", "CompanyCard")

    // Category manager – pull from DB
    val context = LocalContext.current
    val db = remember { AppDatabase.get(context.applicationContext) }
    val categoryVm: CategoryViewModel = viewModel(
        factory = CategoryVMFactory(
            db.expenseCategoryDao(),
            db.expenseDao() // or whatever your DB method is called
        )
    )
    val categoriesFromDb by categoryVm.categories.collectAsState()

    // Fallback if DB empty (e.g. before seeding)
    val categories: List<String> =
        if (categoriesFromDb.isNotEmpty()) {
            categoriesFromDb
                .sortedBy { it.sortOrder }
                .map { it.name }
        } else {
            listOf("General", "Travel", "Meals", "Supplies", "Software", "Training", "Other")
        }

    // Local state (saveable across rotation)
    var amountText by rememberSaveable { mutableStateOf("") }

    var amountFromOcr by rememberSaveable { mutableStateOf(false) }
    var hasTriedOcr by rememberSaveable { mutableStateOf(false) }
    var detectedDateFromOcr by rememberSaveable { mutableStateOf<String?>(null) }

    var status by rememberSaveable { mutableStateOf(ExpenseStatus.Submitted) }

    var category by rememberSaveable { mutableStateOf(ExpenseDefaults.category) }

    LaunchedEffect(categoriesFromDb) {
        if (category.isBlank() && categories.isNotEmpty()) {
            category = categories.first()
        }
    }

    val chipCategories =
        if (category.isNotBlank() && !categories.contains(category)) listOf(category) + categories
        else categories

    var merchant by rememberSaveable { mutableStateOf("") }
    var merchantExpanded by remember { mutableStateOf(false) }
    var merchantSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var reimbursable by rememberSaveable { mutableStateOf(ExpenseDefaults.reimbursable) }
    var paymentMethod by rememberSaveable { mutableStateOf(ExpenseDefaults.paymentMethod) }

    var vatRatePercent by rememberSaveable { mutableStateOf(ExpenseDefaults.vatRatePercent) }
    var vatAdjustmentPence by rememberSaveable { mutableStateOf(0) }

    var manualVatEnabled by rememberSaveable { mutableStateOf(false) }
    var manualVatText by rememberSaveable { mutableStateOf("") } // VAT amount in £

    var existing by remember { mutableStateOf<Expense?>(null) }
    val scope = rememberCoroutineScope()

    val focus = LocalFocusManager.current

    var receiptLocalUri by rememberSaveable(expenseId, initialReceiptUri) {
        mutableStateOf<String?>(initialReceiptUri)
    }

    // list of attachment URIs
    var attachmentUris by rememberSaveable(expenseId, initialReceiptUri) {
        mutableStateOf<List<String>>(emptyList())
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    var attemptedSave by rememberSaveable { mutableStateOf(false) }
    var manualVatTouched by rememberSaveable { mutableStateOf(false) }

    fun parseAmountOrNull(text: String): Double? =
        text.trim().replace(",", ".").toDoubleOrNull()

    // Validation
    val amountValue = parseAmountOrNull(amountText)

    val amountError = attemptedSave && (amountValue == null || amountValue <= 0.0)

    val categoryError = attemptedSave && category.isBlank()

    // Manual VAT validation
    val grossForManual = amountValue
    val manualVatParsed = manualVatText.replace(",", ".").toDoubleOrNull()

    val manualVatError =
        manualVatEnabled && (attemptedSave || manualVatTouched) && (
                manualVatText.isBlank() ||
                        manualVatParsed == null ||
                        manualVatParsed < 0.0 ||
                        (grossForManual != null && manualVatParsed > grossForManual)
                )

    val canSave =
        (amountValue != null && amountValue > 0.0) &&
                !categoryError &&
                (!manualVatEnabled || !manualVatError)

    // Date handling
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("d MMM yyyy") // e.g. "9 Dec 2025"
    }

    var dateMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    val selectedDate: LocalDate = remember(dateMillis) {
        Instant.ofEpochMilli(dateMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
    }

    // For showing a Material3 DatePicker dialog
    var showDatePicker by remember { mutableStateOf(false) }

    val doSave: () -> Unit = doSave@{
        attemptedSave = true

        if (category.isBlank()) return@doSave

        if (amountValue == null || amountValue <= 0.0) return@doSave
        if (manualVatEnabled && manualVatError) return@doSave

        // Recalculate validation based on *current* text
        val currentAmount = parseAmountOrNull(amountText)
        val currentAmountError = currentAmount == null || currentAmount <= 0.0

        if (currentAmountError) {
            // Just show errors and don't save
            return@doSave
        }

        scope.launch {
            val amt = currentAmount
            if (amt == null) return@launch  // extra safety, should never hit

            // Auto-generate a title for list/export:
            // Prefer "Merchant – Category", then Merchant, then Category, then "Expense"
            val autoTitle = when {
                merchant.isNotBlank() && category.isNotBlank() -> "${merchant.trim()} – ${category.trim()}"
                merchant.isNotBlank() -> merchant.trim()
                category.isNotBlank() -> category.trim()
                else -> "Expense"
            }

            val grossPence = (amt * 100.0).roundToInt()

            val manualVatPence: Int? =
                if (manualVatEnabled) {
                    val parsed = manualVatText.replace(",", ".").toDoubleOrNull()
                        ?: run {
                            Toast.makeText(context, "Enter VAT like 3.33", Toast.LENGTH_SHORT).show()
                            return@launch
                        }

                    val pence = (parsed * 100.0).roundToInt()
                    pence.coerceIn(0, grossPence)
                } else null

            val updated = (existing ?: Expense(
                title = autoTitle,
                amount = amt,
                status = status
            )).copy(
                title = autoTitle,
                amount = amt,
                timestamp = dateMillis, // ✅ use picked date
                status = status,
                category = category,
                merchant = merchant.ifBlank { null },
                notes = notes.ifBlank { null },
                reimbursable = reimbursable,
                paymentMethod = paymentMethod,

                attachmentUris = attachmentUris,
                hasReceipt = attachmentUris.isNotEmpty(),
                receiptUri = attachmentUris.firstOrNull(),

                vatRatePercent = vatRatePercent,
                vatAdjustmentPence = if (manualVatPence != null) 0 else vatAdjustmentPence,
                vatManualPence = manualVatPence
            )

            if (existing == null) {
                // New expense – save and remember these as the new defaults
                viewModel.add(updated)

                ExpenseDefaults.apply {
                    category = updated.category
                    reimbursable = updated.reimbursable
                    paymentMethod = updated.paymentMethod
                    vatRatePercent = updated.vatRatePercent
                }
            } else {
                // Editing existing – just update
                viewModel.update(updated)
            }

            onDone()
        }
    }

    // Duplicate the current expense as a new row
    val doCopy: () -> Unit = fun() {
        val original = existing ?: return  // now this is allowed

        scope.launch {
            val copy = original.copy(
                id = 0, // let Room assign a new ID
                timestamp = System.currentTimeMillis()
            )

            viewModel.add(copy)

            Toast
                .makeText(context, "Copied as new expense", Toast.LENGTH_SHORT)
                .show()

            onDone()
        }
    }

    // Try OCR once when we first get a receipt image and amount is still blank
    LaunchedEffect(receiptLocalUri) {
        if (!receiptLocalUri.isNullOrBlank() && amountText.isBlank() && !hasTriedOcr) {
            hasTriedOcr = true
            amountFromOcr = false
            detectedDateFromOcr = null

            val uri = Uri.parse(receiptLocalUri)

            // Fire-and-forget: OCR runs on background thread and calls back
            ReceiptOcrHelper.extractTotalAndDateFromReceipt(context, uri) { result ->
                // Total
                if (result.total != null && amountText.isBlank()) {
                    amountText = String.format("%.2f", result.total)
                    amountFromOcr = true
                }

                // Date – we just show it as a hint for now
                if (result.date != null && detectedDateFromOcr == null) {
                    detectedDateFromOcr = result.date
                }
            }
        }
    }

    // Prefill when editing
    LaunchedEffect(expenseId) {
        if (expenseId != null) {
            viewModel.get(expenseId)?.let { e ->
                existing = e
                amountText = e.amount.toString()
                status = e.status
                category = e.category
                merchant = e.merchant.orEmpty()
                notes = e.notes.orEmpty()
                reimbursable = e.reimbursable
                paymentMethod = e.paymentMethod

                // NEW: load attachments list and keep single receiptLocalUri in sync
                attachmentUris = when {
                    e.attachmentUris.isNotEmpty() -> e.attachmentUris
                    !e.receiptUri.isNullOrBlank() -> listOf(e.receiptUri)
                    else -> emptyList()
                }
                receiptLocalUri = attachmentUris.firstOrNull()

                vatRatePercent = e.vatRatePercent
                vatAdjustmentPence = e.vatAdjustmentPence
                dateMillis = e.timestamp

                manualVatEnabled = e.vatManualPence != null
                manualVatText = e.vatManualPence?.let { String.format("%.2f", it / 100.0) }.orEmpty()
            }
        } else if (!initialReceiptUri.isNullOrBlank()) {
            // New expense launched with a receipt (quick add)
            attachmentUris = listOf(initialReceiptUri)
            receiptLocalUri = initialReceiptUri
        }
    }

    // Load suggestions as user types
    LaunchedEffect(merchant) {
        merchantSuggestions =
            if (merchant.length >= 1) viewModel.suggestMerchants(merchant) else emptyList()
        merchantExpanded = merchantSuggestions.isNotEmpty()
    }

    Scaffold(
        topBar = {
            // Overflow menu for actions like "Copy expense"
            var showMenu by remember { mutableStateOf(false) }

            TopAppBar(
                title = { Text(if (expenseId != null) "Edit Expense" else "New Expense") },
                actions = {
                    if (expenseId != null) {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More actions"
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Copy to new") },
                                onClick = {
                                    showMenu = false
                                    doCopy()
                                }
                            )
                        }
                    }
                }
            )
        },
        // Fixed bottom action bar (stays above keyboard)
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (expenseId != null) {
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Delete")
                        }

                        OutlinedButton(
                            onClick = { doCopy() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Copy")
                        }
                    }

                    Button(
                        onClick = { doSave() },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            MultiAttachmentSection(
                expenseId = expenseId,
                attachments = attachmentUris,
                onAdd = { uri ->
                    val u = uri.toString()
                    val newList = if (!attachmentUris.contains(u)) {
                        attachmentUris + u
                    } else {
                        attachmentUris
                    }
                    attachmentUris = newList
                    receiptLocalUri = newList.firstOrNull()

                    expenseId?.let { viewModel.attachReceipt(it, uri) }
                },
                onRemove = { uriString ->
                    val newList = attachmentUris.filterNot { it == uriString }
                    attachmentUris = newList
                    receiptLocalUri = newList.firstOrNull()

                    expenseId?.let { id ->
                        viewModel.removeReceipt(id)
                    }
                },
                onMove = { from, to ->
                    // Reorder attachmentUris list
                    val mutable = attachmentUris.toMutableList()
                    val item = mutable.removeAt(from)
                    mutable.add(to, item)
                    attachmentUris = mutable
                    receiptLocalUri = mutable.firstOrNull()
                },
                autoLaunchCamera = startWithCamera
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(modifier = Modifier.weight(1f))

                TextButton(onClick = { showDatePicker = true }) {
                    Text(selectedDate.format(dateFormatter))
                }
            }

            Spacer(Modifier.height(16.dp))

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                isError = amountError,
                supportingText = {
                    if (amountError) {
                        Text("Enter a number > 0, e.g. 4.50")
                    } else {
                        Column {
                            Text("Treated as gross (includes ${vatRatePercent}% VAT). Breakdown shown below.")
                            if (amountFromOcr) {
                                Text(
                                    text = "Amount detected from receipt",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (detectedDateFromOcr != null) {
                                Text(
                                    text = "Detected date: ${detectedDateFromOcr}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focus.clearFocus()
                        doSave()
                    }
                )
            )

            // VAT RATE SELECTOR
            val vatOptions = listOf(0, 5, 20)
            var rateExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = rateExpanded,
                onExpandedChange = { shouldExpand ->
                    if (!manualVatEnabled) {
                        rateExpanded = shouldExpand
                    } else {
                        rateExpanded = false
                    }
                }
            ) {
            OutlinedTextField(
                    value = "$vatRatePercent%",
                    onValueChange = {},
                    label = { Text("VAT rate") },
                    readOnly = true,
                    enabled = !manualVatEnabled,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = rateExpanded,
                    onDismissRequest = { rateExpanded = false }
                ) {
                    vatOptions.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("$rate%") },
                            onClick = {
                                vatRatePercent = rate
                                rateExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Manual VAT amount", style = MaterialTheme.typography.labelLarge)
                    Text(
                        if (manualVatEnabled) "Overrides VAT rate + adjustment."
                        else "Off = VAT uses the rate above (and you can use ±1p).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = manualVatEnabled,
                    onCheckedChange = { enabled ->
                        manualVatEnabled = enabled
                        if (enabled) {
                            vatAdjustmentPence = 0
                        } else {
                            manualVatText = ""
                        }
                    }
                )
            }

            OutlinedTextField(
                value = manualVatText,
                onValueChange = {
                    manualVatTouched = true
                    manualVatText = it.replace(",", ".")
                },
                label = { Text("VAT amount (£)") },
                enabled = manualVatEnabled,
                isError = manualVatError,
                supportingText = {
                    if (manualVatEnabled) {
                        Text(
                            when {
                                manualVatText.isBlank() -> "Enter VAT from receipt (e.g. 1.23)"
                                manualVatParsed == null -> "Enter a valid number (e.g. 1.23)"
                                manualVatParsed < 0.0 -> "VAT can’t be negative"
                                grossForManual != null && manualVatParsed > grossForManual -> "VAT can’t be more than gross"
                                else -> "Net will be calculated as Gross − VAT"
                            }
                        )
                    } else {
                        Text("Optional – useful for mixed VAT receipts.")
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Spacer(Modifier.height(12.dp))

// --- Breakdown calculation ---
            val currency = remember { java.text.NumberFormat.getCurrencyInstance() }

            val grossAmount = amountText.replace(",", ".").toDoubleOrNull()
            val grossPenceUi = grossAmount?.let { (it * 100.0).roundToInt() }

            val manualVatPenceUi =
                if (manualVatEnabled) manualVatText.replace(",", ".").toDoubleOrNull()?.let { (it * 100.0).roundToInt() }
                else null

            val safeManualVatPenceUi =
                if (grossPenceUi != null && manualVatPenceUi != null) manualVatPenceUi.coerceIn(0, grossPenceUi)
                else null

            val vatRate = vatRatePercent / 100.0
            val baseNet = grossAmount?.let { it / (1.0 + vatRate) }
            val baseVat = if (grossAmount != null && baseNet != null) grossAmount - baseNet else null

            val adjustedVat = baseVat?.let { it + vatAdjustmentPence / 100.0 }

            val vatToShow =
                if (safeManualVatPenceUi != null) safeManualVatPenceUi / 100.0
                else adjustedVat

            val netToShow =
                if (grossAmount != null && vatToShow != null) grossAmount - vatToShow
                else baseNet

            if (grossAmount != null && netToShow != null && vatToShow != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "NET / VAT / GROSS breakdown",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Net", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currency.format(netToShow), style = MaterialTheme.typography.titleMedium)
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (manualVatEnabled) "VAT (manual)" else "VAT (${vatRatePercent}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(currency.format(vatToShow), style = MaterialTheme.typography.titleMedium)

                                if (!manualVatEnabled) {
                                    Spacer(Modifier.height(6.dp))

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { vatAdjustmentPence -= 1 },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) { Text("−1p") }

                                        TextButton(
                                            onClick = { vatAdjustmentPence += 1 },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) { Text("+1p") }
                                    }

                                    Text(
                                        text = when {
                                            vatAdjustmentPence == 0 -> "Match invoice"
                                            vatAdjustmentPence > 0 -> "+${vatAdjustmentPence}p"
                                            else -> "${vatAdjustmentPence}p"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gross", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currency.format(grossAmount), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Net + VAT = Gross",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Status
            StatusPicker(value = status, onValueChange = { status = it })
            Spacer(Modifier.height(16.dp))

            ExposedDropdownMenuBox(
                expanded = merchantExpanded,
                onExpandedChange = { merchantExpanded = it }
            ) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = {
                        merchant = it
                        merchantExpanded = true
                    },
                    label = { Text("Merchant") },
                    singleLine = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = merchantExpanded,
                    onDismissRequest = { merchantExpanded = false }
                ) {
                    merchantSuggestions.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s) },
                            onClick = {
                                merchant = s
                                merchantExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // Category
            Text("Category", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                chipCategories.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) }
                    )
                }
            }

            if (categoryError) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Please pick a category",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Reimbursement & payment",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Reimbursable
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reimbursable")
                        Switch(
                            checked = reimbursable,
                            onCheckedChange = { reimbursable = it }
                        )
                    }

                    // Payment method
                    Text(
                        text = "Payment method",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        paymentMethods.forEach { pm ->
                            FilterChip(
                                selected = paymentMethod == pm,
                                onClick = { paymentMethod = pm },
                                label = { Text(if (pm == "CompanyCard") "Company card" else pm) }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                supportingText = {
                    Text("Optional – add anything your approver should know")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(Modifier.height(80.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = dateMillis
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newMillis = pickerState.selectedDateMillis
                        if (newMillis != null) {
                            dateMillis = newMillis
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDeleteConfirm && expenseId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete expense") },
            text = { Text("Are you sure you want to delete this expense? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            val inferredTitle = existing?.title
                                ?: merchant.takeIf { it.isNotBlank() }
                                ?: category.takeIf { it.isNotBlank() }
                                ?: "-"

                            val inferredAmount = amountText.toDoubleOrNull() ?: 0.0

                            val toDelete = existing ?: Expense(
                                id = expenseId,
                                title = inferredTitle,
                                amount = inferredAmount,
                                status = status
                            )
                            viewModel.delete(toDelete)
                            onDone()
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPicker(
    value: ExpenseStatus,
    onValueChange: (ExpenseStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val options = ExpenseStatus.values()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value.name,
            onValueChange = {},
            label = { Text("Status") },
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { st ->
                DropdownMenuItem(
                    text = { Text(st.name) },
                    onClick = {
                        onValueChange(st)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun MultiAttachmentSection(
    expenseId: Long?,
    attachments: List<String>,
    onAdd: (Uri) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    autoLaunchCamera: Boolean = false
) {
    val context = LocalContext.current
    var showPreviewUri by remember { mutableStateOf<String?>(null) }

    // Pick file
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            onAdd(uri)
        }
    }

    // Take photo
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) pendingUri?.let(onAdd)
        else pendingUri?.let { context.contentResolver.delete(it, null, null) }
        pendingUri = null
    }

    // Auto-launch camera (quick add)
    LaunchedEffect(autoLaunchCamera) {
        if (autoLaunchCamera && attachments.isEmpty()) {
            val uri = createImageUri(context)
            pendingUri = uri
            if (uri != null) camera.launch(uri)
        }
    }

    Column(Modifier.fillMaxWidth()) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Attachments", style = MaterialTheme.typography.titleMedium)

            Spacer(Modifier.weight(1f))

            // simple count “pill”
            if (attachments.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = "${attachments.size}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Add photos or PDFs. Tap a tile to preview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        // ---- PREVIEW AREA ----
        if (attachments.isNotEmpty()) {
            if (attachments.size == 1) {
                // Single attachment – big, full-width card
                val uriString = attachments.first()
                val uri = Uri.parse(uriString)
                val mime = context.contentResolver.getType(uri)
                val isPdf = mime == "application/pdf" || uriString.lowercase().endsWith(".pdf")

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clickable {
                            if (isPdf) {
                                val i = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { context.startActivity(i) }
                            } else {
                                showPreviewUri = uriString
                            }
                        },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        if (isPdf) {
                            PdfFirstPageThumb(uri)
                        } else {
                            Image(
                                painter = rememberAsyncImagePainter(uri),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        TextButton(
                            onClick = { onRemove(uriString) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) { Text("Remove") }
                    }
                }
            } else {
                // Multiple attachments – tiles in a row, with reorder arrows
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(attachments) { index, uriString ->
                        val uri = Uri.parse(uriString)
                        val mime = context.contentResolver.getType(uri)
                        val isPdf = mime == "application/pdf" || uriString.lowercase().endsWith(".pdf")

                        Card(
                            modifier = Modifier
                                .height(180.dp)
                                .width(180.dp)
                                .clickable {
                                    if (isPdf) {
                                        val i = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "application/pdf")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        runCatching { context.startActivity(i) }
                                    } else {
                                        showPreviewUri = uriString
                                    }
                                },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                if (isPdf) {
                                    PdfFirstPageThumb(uri)
                                } else {
                                    Image(
                                        painter = rememberAsyncImagePainter(uri),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Remove
                                TextButton(
                                    onClick = { onRemove(uriString) },
                                    modifier = Modifier.align(Alignment.TopEnd)
                                ) { Text("Remove") }

                                // Reorder arrows (bottom center)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (index > 0) onMove(index, index - 1) },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowBack,
                                            contentDescription = "Move left"
                                        )
                                    }

                                    IconButton(
                                        onClick = { if (index < attachments.size - 1) onMove(index, index + 1) },
                                        enabled = index < attachments.size - 1
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowForward,
                                            contentDescription = "Move right"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        // ---- ACTION BUTTONS ----
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    val uri = createImageUri(context)
                    pendingUri = uri
                    if (uri != null) camera.launch(uri)
                },
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Take photo")
            }

            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*", "application/pdf")) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AttachFile, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add file")
            }
        }
    }

    // Full-screen preview for images
    if (showPreviewUri != null) {
        val uri = Uri.parse(showPreviewUri!!)
        AlertDialog(
            onDismissRequest = { showPreviewUri = null },
            confirmButton = {
                TextButton(onClick = { showPreviewUri = null }) {
                    Text("Close")
                }
            },
            text = {
                Image(
                    painter = rememberAsyncImagePainter(uri),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(420.dp)
                )
            }
        )
    }
}

@Composable
private fun PdfFirstPageThumb(uri: Uri) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount > 0) {
                            renderer.openPage(0).use { page ->
                                val scale = 2
                                val width = page.width * scale
                                val height = page.height * scale
                                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        } else null
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = "PDF thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("PDF", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun createImageUri(context: Context): Uri? {
    val name = "receipt_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Shows in Photos > Albums > SimpleExpenses (Pictures/SimpleExpenses)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SimpleExpenses")
        }
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}