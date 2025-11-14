package com.example.simpleexpenses.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.work.WorkManager
import com.example.simpleexpenses.notify.ReminderScheduler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun SettingsScreen(
    mileageVM: MileageViewModel,  // reuse the VM that already exposes settings
    themeMode: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit,
    onOpenAppInfo: () -> Unit,
    onBack: () -> Unit
) {
    val ui by mileageVM.ui.collectAsState()
    val settings = ui.settings
    val reminderEnabled = settings?.reminderEnabled == true
    val scope = rememberCoroutineScope()
    val context = LocalAppHolder.current // see helper below
    val wm = remember { WorkManager.getInstance(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Mileage", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Use HMRC rates", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings?.useHmrc == true,
                    onCheckedChange = { checked ->
                        scope.launch { mileageVM.setUseHmrc(checked) }
                    }
                )
            }

            OutlinedTextField(
                value = settings?.customRatePence?.toString().orEmpty(),
                onValueChange = { txt ->
                    val p = txt.filter { it.isDigit() }.toIntOrNull() ?: 0
                    scope.launch { mileageVM.setCustomRatePence(p) }
                },
                label = { Text("Custom rate (pence/mi)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = settings?.useHmrc == false,
                modifier = Modifier.fillMaxWidth()
            )

            Divider()

            Text("Reminders", style = MaterialTheme.typography.titleMedium)

            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("Daily reminder enabled", modifier = Modifier.weight(1f))
                Switch(
                    checked = settings?.reminderEnabled == true,
                    onCheckedChange = { checked ->
                        scope.launch {
                            mileageVM.setReminderEnabled(checked)
                            val h = settings?.reminderHour ?: 19
                            val m = settings?.reminderMinute ?: 0
                            if (checked) {
                                ReminderScheduler.scheduleDaily(wm, h, m,
                                    title = "Daily reminder",
                                    message = "Log today’s expenses/mileage.")
                            } else {
                                ReminderScheduler.cancelAll(wm)
                            }
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = settings?.reminderHour?.toString().orEmpty(),
                    onValueChange = { txt ->
                        val h = txt.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0,23) ?: 19
                        scope.launch {
                            mileageVM.setReminderTime(h, settings?.reminderMinute ?: 0)
                        }
                    },
                    label = { Text("Hour (0–23)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = reminderEnabled,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = settings?.reminderMinute?.toString().orEmpty(),
                    onValueChange = { txt ->
                        val m = txt.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0,59) ?: 0
                        scope.launch {
                            mileageVM.setReminderTime(settings?.reminderHour ?: 19, m)
                        }
                    },
                    label = { Text("Minute (0–59)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = reminderEnabled,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        scope.launch {
                            val h = settings?.reminderHour ?: 19
                            val m = settings?.reminderMinute ?: 0
                            ReminderScheduler.scheduleDaily(wm, h, m,
                                title = "Daily reminder",
                                message = "Log today’s expenses/mileage.")
                            mileageVM.setReminderEnabled(true)
                        }
                    }
                ) { Text("Apply") }
            }

            Divider()

            Text("Appearance", style = MaterialTheme.typography.titleMedium)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeChoiceChip(
                    label = "System",
                    mode = AppThemeMode.SYSTEM,
                    current = themeMode,
                    onSelected = onThemeChange,
                    modifier = Modifier.weight(1f)
                )
                ThemeChoiceChip(
                    label = "Light",
                    mode = AppThemeMode.LIGHT,
                    current = themeMode,
                    onSelected = onThemeChange,
                    modifier = Modifier.weight(1f)
                )
                ThemeChoiceChip(
                    label = "Dark",
                    mode = AppThemeMode.DARK,
                    current = themeMode,
                    onSelected = onThemeChange,
                    modifier = Modifier.weight(1f)
                )
            }

            Divider()

            Text(
                "About",
                style = MaterialTheme.typography.titleMedium
            )

            TextButton(onClick = onOpenAppInfo) {
                Text("App info")
            }
        }
    }
}

/** Minimal holder for application context inside Compose */
object LocalAppHolder {
    val current: android.content.Context
        @Composable get() = androidx.compose.ui.platform.LocalContext.current.applicationContext
}

@Composable
private fun ThemeChoiceChip(
    label: String,
    mode: AppThemeMode,
    current: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = current == mode
    OutlinedButton(
        onClick = { onSelected(mode) },
        enabled = !selected,
        modifier = modifier
    ) {
        Text(label)
    }
}
