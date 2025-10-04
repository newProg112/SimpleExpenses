package com.example.simpleexpenses.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.example.simpleexpenses.data.Expense
import com.example.simpleexpenses.data.ExpenseStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExpenseEditScreen(
    viewModel: ExpenseViewModel,
    expenseId: Long? = null,
    onDone: () -> Unit
) {
    // Preset options
    val categories = listOf("General", "Travel", "Meals", "Supplies", "Software", "Training", "Other")
    val paymentMethods = listOf("Personal", "CompanyCard")

    // Local state (saveable across rotation)
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf(ExpenseStatus.Submitted) }

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

    var receiptLocalUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Validation
    val amount = amountText.toDoubleOrNull()
    val amountError = amount == null || amount <= 0.0
    val canSave = title.isNotBlank() && !amountError

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
                receiptUri = receiptLocalUri,
                hasReceipt = !receiptLocalUri.isNullOrBlank()
            )
            if (existing == null) viewModel.add(updated) else viewModel.update(updated)
            onDone()
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
                receiptLocalUri = e.receiptUri
            }
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
                            onClick = {
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
            // Title
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Down) }
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
                    if (amountError) Text("Enter a number > 0, e.g. 4.50")
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

            // Reimbursable
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Reimbursable")
                Switch(checked = reimbursable, onCheckedChange = { reimbursable = it })
            }
            Spacer(Modifier.height(12.dp))

            // Payment method
            Text("Payment method", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 6.dp))
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
            Spacer(Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            ReceiptSection(
                expenseId = expenseId,
                currentReceiptUri = receiptLocalUri,   // 👈 use screen state
                onPick = { uri ->
                    receiptLocalUri = uri.toString()   // 👈 keep local in sync
                    expenseId?.let { viewModel.attachReceipt(it, uri) } // existing items update DB
                },
                onRemove = {
                    receiptLocalUri = null             // 👈 keep local in sync
                    expenseId?.let { viewModel.removeReceipt(it) }
                }
            )

            Spacer(Modifier.height(80.dp)) // breathing room above bottom bar
        }
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
fun ReceiptSection(
    expenseId: Long?,
    currentReceiptUri: String?,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var localUri by remember(currentReceiptUri) { mutableStateOf(currentReceiptUri) }
    var showPreview by remember { mutableStateOf(false) }

    // ---- PICK FILE (kept) ----
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist read permission so we can keep loading it later
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) { /* already persisted or not persistable */ }

            onPick(uri)                  // always update caller
            localUri = uri.toString()    // optimistic UI
        }
    }

    // ---- TAKE PHOTO (new) ----
    var pendingCaptureUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCaptureUri?.let { captured ->
                onPick(captured)                 // update screen + DB (if existing)
                localUri = captured.toString()   // optimistic UI
            }
        } else {
            // If capture cancelled, clean up any empty row inserted into MediaStore
            pendingCaptureUri?.let { context.contentResolver.delete(it, null, null) }
        }
        pendingCaptureUri = null
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Take photo (camera)
        Button(onClick = {
            val uri = createImageUri(context)
            pendingCaptureUri = uri
            if (uri != null) cameraLauncher.launch(uri)
        }) { Text("Take photo") }

        // Pick file (document picker)
        Button(onClick = { picker.launch(arrayOf("image/*")) }) {
            Text(if (localUri.isNullOrEmpty()) "Pick file" else "Replace")
        }

        if (!localUri.isNullOrEmpty()) {
            OutlinedButton(onClick = {
                onRemove()
                localUri = null
            }) { Text("Remove") }
        }
    }

    if (!localUri.isNullOrEmpty()) {
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable { showPreview = true }
        ) {
            Image(
                painter = rememberAsyncImagePainter(model = Uri.parse(localUri)),
                contentDescription = "Receipt",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (showPreview && !localUri.isNullOrEmpty()) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            confirmButton = { TextButton(onClick = { showPreview = false }) { Text("Close") } },
            text = {
                Image(
                    painter = rememberAsyncImagePainter(model = Uri.parse(localUri)),
                    contentDescription = "Receipt full screen",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                )
            }
        )
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