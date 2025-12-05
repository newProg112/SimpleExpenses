package com.example.simpleexpenses.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import coil.compose.rememberAsyncImagePainter
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseStatus
import com.example.simpleexpenses.ocr.ReceiptOcrHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    viewModel: ExpenseViewModel,
    expenseId: Long? = null,
    initialReceiptUri: String? = null,
    startWithCamera: Boolean = false,
    onDone: () -> Unit
) {
    // Preset options
    val categories = listOf("General", "Travel", "Meals", "Supplies", "Software", "Training", "Other")
    val paymentMethods = listOf("Personal", "CompanyCard")

    // Local state (saveable across rotation)
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }

    var amountFromOcr by rememberSaveable { mutableStateOf(false) }
    var hasTriedOcr by rememberSaveable { mutableStateOf(false) }
    var detectedDateFromOcr by rememberSaveable { mutableStateOf<String?>(null) }

    var status by rememberSaveable { mutableStateOf(ExpenseStatus.Submitted) }

    var titleTouched by rememberSaveable { mutableStateOf(false) }

    var category by rememberSaveable { mutableStateOf("General") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var merchantExpanded by remember { mutableStateOf(false) }
    var merchantSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var notes by rememberSaveable { mutableStateOf("") }
    var reimbursable by rememberSaveable { mutableStateOf(true) }
    var paymentMethod by rememberSaveable { mutableStateOf("Personal") }

    var existing by remember { mutableStateOf<Expense?>(null) }
    val scope = rememberCoroutineScope()

    val focus = LocalFocusManager.current

    var receiptLocalUri by rememberSaveable(expenseId, initialReceiptUri) {
        mutableStateOf<String?>(initialReceiptUri)
    }

    // list of attachment URIs
    var attachmentUris by rememberSaveable(expenseId) {
        mutableStateOf<List<String>>(emptyList())
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Validation
    val amount = amountText.toDoubleOrNull()
    val amountError = amount == null || amount <= 0.0

    val titleError = titleTouched && title.isBlank()

    val canSave = !titleError && !amountError

    var vatRatePercent by rememberSaveable { mutableStateOf(20) }

    var vatAdjustmentPence by rememberSaveable { mutableStateOf(0) }

    val doSave: () -> Unit = save@{
        if (!canSave) return@save
        scope.launch {
            val amt = amount ?: return@launch
            val updated = (existing ?: Expense(
                title = title,
                amount = amt,
                status = status
            )).copy(
                title = title,
                amount = amt,
                status = status,
                category = category,
                merchant = merchant.ifBlank { null },
                notes = notes.ifBlank { null },
                reimbursable = reimbursable,
                paymentMethod = paymentMethod,

                attachmentUris = attachmentUris,
                hasReceipt = attachmentUris.isNotEmpty(),
                receiptUri = attachmentUris.firstOrNull(),

                vatAdjustmentPence = vatAdjustmentPence,
                vatRatePercent = vatRatePercent
            )
            if (existing == null) viewModel.add(updated) else viewModel.update(updated)
            onDone()
        }
    }

    val context = LocalContext.current

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
                title = e.title
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
            TopAppBar(title = { Text(if (expenseId != null) "Edit Expense" else "New Expense") })
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
                            onClick = { showDeleteConfirm = true }
                        ) { Text("Delete") }
                    }

                    Button(
                        onClick = { doSave() },
                        enabled = canSave,
                        modifier = Modifier.weight(1f)
                    ) { Text("Save") }
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

            // Title
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    if (!titleTouched) titleTouched = true
                },
                label = { Text("Title") },
                isError = titleError,
                supportingText = {
                    if (titleError) Text("Title can’t be empty")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = {
                        if (!titleTouched) titleTouched = true
                        focus.moveFocus(FocusDirection.Down)
                    }
                )
            )

            Spacer(Modifier.height(12.dp))

            // Amount
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.replace(',', '.') },
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
                onExpandedChange = { rateExpanded = it }
            ) {
                OutlinedTextField(
                    value = "$vatRatePercent%",
                    onValueChange = {},
                    label = { Text("VAT rate") },
                    readOnly = true,
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

            Spacer(Modifier.height(12.dp))

            val vatRate = vatRatePercent / 100.0
            val grossAmount = amountText.replace(",", "").toDoubleOrNull()
            val baseNet = grossAmount?.let { it / (1.0 + vatRate) }
            val baseVat = if (grossAmount != null && baseNet != null) {
                grossAmount - baseNet
            } else null

            // Apply adjustment (in pence) to VAT, and back-calc NET = GROSS - VAT
            val adjustedVat = baseVat?.let { it + vatAdjustmentPence / 100.0 }
            val adjustedNet = if (grossAmount != null && adjustedVat != null) {
                grossAmount - adjustedVat
            } else null

            val netToShow = adjustedNet ?: baseNet
            val vatToShow = adjustedVat ?: baseVat

            val currency = remember { java.text.NumberFormat.getCurrencyInstance() }

            if (grossAmount != null && netToShow != null && vatToShow != null) {
                Spacer(Modifier.height(8.dp))

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
                            // Net
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Net (${vatRatePercent}% VAT)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currency.format(netToShow),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            // VAT
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "VAT (${vatRatePercent}%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = currency.format(vatToShow),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Spacer(Modifier.height(6.dp))

                                Column(
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
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

                            // Gross
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Gross",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currency.format(grossAmount),
                                    style = MaterialTheme.typography.titleMedium
                                )
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
                categories.forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = { category = c },
                        label = { Text(c) }
                    )
                }
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
                            val toDelete = existing ?: Expense(
                                id = expenseId,
                                title = if (title.isBlank()) "-" else title,
                                amount = amount ?: 0.0,
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
        Text("Attachments", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // ---- PREVIEW AREA ----
        if (attachments.isNotEmpty()) {
            if (attachments.size == 1) {
                // Single attachment – big, full-width card
                val uriString = attachments.first()
                val uri = Uri.parse(uriString)
                val mime = context.contentResolver.getType(uri)
                val isPdf = mime == "application/pdf" ||
                        uriString.lowercase().endsWith(".pdf")

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
                        ) {
                            Text("Remove")
                        }
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
                        val isPdf = mime == "application/pdf" ||
                                uriString.lowercase().endsWith(".pdf")

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
                                ) {
                                    Text("Remove")
                                }

                                // Reorder arrows (bottom center)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                onMove(index, index - 1)
                                            }
                                        },
                                        enabled = index > 0
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ArrowBack,
                                            contentDescription = "Move left"
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index < attachments.size - 1) {
                                                onMove(index, index + 1)
                                            }
                                        },
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
            Button(onClick = {
                val uri = createImageUri(context)
                pendingUri = uri
                if (uri != null) camera.launch(uri)
            }) {
                Text("Take photo")
            }

            Button(onClick = {
                picker.launch(arrayOf("image/*", "application/pdf"))
            }) {
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