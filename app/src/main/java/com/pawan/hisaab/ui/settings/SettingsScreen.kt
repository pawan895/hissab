package com.pawan.hisaab.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.ui.MainViewModel
import com.pawan.hisaab.security.AppLock
import com.pawan.hisaab.ui.components.Hairline
import com.pawan.hisaab.ui.components.PlainField
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.theme.FinTheme

val CSV_MIME_TYPES = arrayOf("text/csv", "text/comma-separated-values", "application/csv", "text/plain", "text/*")

@Composable
fun SettingsScreen(vm: MainViewModel, onManageCategories: () -> Unit, contentPadding: PaddingValues) {
    val popup by vm.settings.popupEnabled.collectAsState()
    val auto by vm.settings.autoCategorize.collectAsState()
    val busy by vm.busy.collectAsState()
    val month by vm.month.collectAsState()
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    val lockOn by vm.lock.enabled.collectAsState()
    val bioOn by vm.lock.biometric.collectAsState()
    val bioAvailable = remember { vm.lock.biometricAvailable() }
    var pinDialog by remember { mutableStateOf<PinDialogMode?>(null) }

    val importSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickedSheet) }
    val exportMonth = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportMonth) }
    val exportTxns = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportTransactions) }
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::backup) }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { confirmRestore = it }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
    ) {
        SectionLabel("Popup")
        SwitchRow("Ask after each payment", "Shows a card when a bank SMS arrives", popup, onChange = vm.settings::setPopupEnabled)
        SwitchRow(
            "File known merchants for me",
            "Zomato, Zepto, Uber and any merchant you have sorted before",
            auto, onChange = vm.settings::setAutoCategorize,
        )
        NavRow("Preview the popup", null, onClick = vm::previewPopup)

        SectionLabel("Security")
        SwitchRow("App lock", "Ask for your PIN when opening Hisaab", lockOn) { on ->
            if (on) vm.lock.setEnabled(true) else pinDialog = PinDialogMode.TurnOff
        }
        if (lockOn) {
            SwitchRow(
                "Unlock with fingerprint or face",
                if (bioAvailable) "Faster than typing the PIN" else "Set up fingerprint or face unlock in phone settings first",
                bioOn && bioAvailable,
                enabled = bioAvailable,
                onChange = vm.lock::setBiometric,
            )
            NavRow("Change PIN", null) { pinDialog = PinDialogMode.Change }
        }

        SectionLabel("Permissions")
        PermissionsChecklist()

        SectionLabel("Data")
        NavRow("Categories", "Add, rename or hide", onClick = onManageCategories)
        NavRow("Scan SMS inbox", "Last 3 months · duplicates are skipped", enabled = !busy) { vm.scanInbox(3) }
        NavRow("Import budget sheet", "CSV downloaded from Google Sheets", enabled = !busy) { importSheet.launch(CSV_MIME_TYPES) }
        NavRow("Export $month to sheet", "Same layout as your Google Sheet", enabled = !busy) { exportMonth.launch("Monthly Budget - $month.csv") }
        NavRow("Export all transactions", "CSV", enabled = !busy) { exportTxns.launch("hisaab-transactions.csv") }
        NavRow("Back up", "Everything, as one JSON file", enabled = !busy) { backup.launch("hisaab-backup.json") }
        NavRow("Restore backup", "Replaces all current data", enabled = !busy) {
            restore.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
        }

        Text(
            "Hisaab has no internet access. Your data stays on this phone.",
            style = MaterialTheme.typography.bodySmall,
            color = FinTheme.colors.ink3,
            modifier = Modifier.padding(vertical = 28.dp),
        )
    }

    pinDialog?.let { mode ->
        PinDialog(
            mode = mode,
            check = { vm.lock.tryPin(it) },
            onDismiss = { pinDialog = null },
            onDone = { newPin ->
                when (mode) {
                    PinDialogMode.TurnOff -> { vm.lock.setEnabled(false); vm.say("App lock turned off") }
                    PinDialogMode.Change -> { vm.lock.changePin(newPin!!); vm.say("PIN changed") }
                }
                pinDialog = null
            },
        )
    }

    confirmRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmRestore = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Replace all data?") },
            text = { Text("Everything currently in the app will be replaced by the backup.") },
            confirmButton = { TextButton(onClick = { vm.restoreBackup(uri); confirmRestore = null }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = FinTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) c.ink else c.ink3)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
            Spacer(Modifier.width(16.dp))
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.ink,
                    checkedThumbColor = MaterialTheme.colorScheme.background,
                    uncheckedTrackColor = c.tint,
                    uncheckedBorderColor = c.hairline,
                    uncheckedThumbColor = c.ink3,
                ),
            )
        }
        Hairline()
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, enabled: Boolean = true, onClick: () -> Unit) {
    val c = FinTheme.colors
    Column {
        Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) c.ink else c.ink3)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
            Text("›", style = MaterialTheme.typography.titleLarge, color = c.ink3)
        }
        Hairline()
    }
}

private enum class PinDialogMode { TurnOff, Change }

/** Confirms the current PIN; in Change mode also collects and confirms a new one. */
@Composable
private fun PinDialog(
    mode: PinDialogMode,
    check: (String) -> AppLock.Result,
    onDismiss: () -> Unit,
    onDone: (newPin: String?) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val change = mode == PinDialogMode.Change
    val nextOk = next.length in 4..6 && next.all { it.isDigit() }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text(if (change) "Change PIN" else "Turn off app lock") },
        text = {
            Column {
                PlainField(current, { current = it.filter(Char::isDigit).take(6); error = null }, "Current PIN", password = true)
                if (change) {
                    PlainField(next, { next = it.filter(Char::isDigit).take(6); error = null }, "New PIN (4–6 digits)", password = true)
                    PlainField(again, { again = it.filter(Char::isDigit).take(6); error = null }, "Repeat new PIN", password = true)
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = FinTheme.colors.over, modifier = Modifier.padding(top = 10.dp))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = current.isNotEmpty() && (!change || (nextOk && again.isNotEmpty())),
                onClick = {
                    error = when {
                        change && next != again -> "New PINs don't match"
                        else -> when (val r = check(current)) {
                            AppLock.Result.Ok -> null.also { onDone(if (change) next else null) }
                            is AppLock.Result.Wrong -> "Wrong PIN · ${r.attemptsLeft} left"
                            is AppLock.Result.LockedOut -> "Too many tries. Try again shortly."
                        }
                    }
                },
            ) { Text(if (change) "Save" else "Turn off") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
