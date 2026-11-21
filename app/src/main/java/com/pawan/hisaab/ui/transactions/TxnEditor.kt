package com.pawan.hisaab.ui.transactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import com.pawan.hisaab.data.formatDate
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.MainViewModel
import com.pawan.hisaab.ui.components.CategoryList
import com.pawan.hisaab.ui.components.Pill
import com.pawan.hisaab.ui.components.PlainField
import com.pawan.hisaab.ui.components.PrimaryButton
import com.pawan.hisaab.ui.components.TextTabs
import com.pawan.hisaab.ui.components.groupedForDirection
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/** Edit an existing transaction, or create a manual/cash one when [txn] is null. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TxnEditorSheet(
    txn: Txn?,
    groups: List<CategoryGroup>,
    categories: List<Category>,
    vm: MainViewModel,
    onDelete: (Txn) -> Unit,
    onClose: () -> Unit,
) {
    val c = FinTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNew = txn == null
    var amount by remember { mutableStateOf(txn?.let { Money.plain(it.amountPaise).removeSuffix(".00") } ?: "") }
    var direction by remember { mutableStateOf(txn?.direction ?: Direction.DEBIT) }
    var merchant by remember { mutableStateOf(txn?.merchantRaw ?: "") }
    var note by remember { mutableStateOf(txn?.note ?: "") }
    var categoryId by remember { mutableStateOf(txn?.categoryId) }
    var timestamp by remember { mutableStateOf(txn?.timestamp ?: System.currentTimeMillis()) }
    var method by remember { mutableStateOf("UPI") }
    var showDate by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    var showSms by remember { mutableStateOf(false) }

    val paise = Money.parsePaise(amount)
    val valid = paise != null && paise > 0
    val merchants by vm.merchants.collectAsState()
    val frequent by produceState(emptyList<Category>(), direction) { value = vm.frequentCategories(direction) }
    val grouped = remember(groups, categories, direction, frequent) {
        val all = groupedForDirection(groups, categories, direction)
        if (isNew && frequent.isNotEmpty()) {
            listOf(CategoryGroup(id = -1, name = "Frequent", kind = GroupKind.EXPENSE) to frequent) + all
        } else {
            all
        }
    }
    // Past payees matching what's typed, so "zom" offers Zomato with its usual category.
    val merchantMatches = remember(merchant, merchants, direction) {
        val q = merchant.trim().lowercase()
        if (q.isEmpty()) emptyList()
        else merchants.filter { it.direction == direction }
            .distinctBy { it.merchantKey }
            .filter { m ->
                val name = MerchantNormalizer.displayName(m.merchantRaw)
                name.lowercase().contains(q) && !name.equals(merchant.trim(), ignoreCase = true)
            }
            .take(4)
    }
    val amountFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (isNew) {
            delay(250)
            runCatching { amountFocus.requestFocus(); keyboard?.show() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding() // the sheet is tall enough to reach the top of the screen
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextTabs(
                options = listOf("Spent", "Received"),
                selected = if (direction == Direction.DEBIT) 0 else 1,
                onSelect = { direction = if (it == 0) Direction.DEBIT else Direction.CREDIT },
            )
            PlainField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = "Amount (₹)",
                keyboardType = KeyboardType.Decimal,
                isError = amount.isNotEmpty() && !valid,
                textStyle = MaterialTheme.typography.displaySmall.merge(Numbers),
                modifier = Modifier.focusRequester(amountFocus),
            )
            PlainField(merchant, { merchant = it }, if (direction == Direction.DEBIT) "Paid to" else "Received from")
            if (merchantMatches.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    merchantMatches.forEach { m ->
                        Pill(MerchantNormalizer.displayName(m.merchantRaw), false, {
                            merchant = MerchantNormalizer.displayName(m.merchantRaw)
                            if (categoryId == null) categoryId = m.categoryId
                        })
                    }
                }
            }
            PlainField(note, { note = it }, "Note")

            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Date", style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.weight(1f))
                Text(
                    timestamp.formatDate("d MMM yyyy, h:mm a"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.accent,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { showDate = true }.padding(4.dp),
                )
            }
            if (txn != null && (txn.bank.isNotBlank() || txn.accountLast4.isNotBlank())) {
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        if (txn.source == TxnSource.SMS) "Account" else "Paid by",
                        style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.weight(1f),
                    )
                    Text(
                        listOf(txn.bank, txn.accountLast4.ifBlank { "" }.let { if (it.isEmpty()) "" else "••$it" })
                            .filter { it.isNotBlank() }.joinToString(" "),
                        style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(4.dp),
                    )
                }
            }
            if (isNew) {
                Text(
                    if (direction == Direction.DEBIT) "Paid by" else "Received by",
                    style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    listOf("UPI", "Card", "Cash", "Other").forEach { m -> Pill(m, method == m, { method = m }) }
                }
            }

            Text("Category", style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.padding(top = 18.dp))
            // Re-create the list when "Frequent" arrives, otherwise it keeps its scroll anchored below it.
            key(grouped.firstOrNull()?.first?.id) {
                CategoryList(groups = grouped, selectedId = categoryId, onPick = { categoryId = it.id }, modifier = Modifier.heightIn(max = 300.dp))
            }

            if (txn?.smsBody != null) {
                Text(
                    if (showSms) "Hide original SMS" else "Show original SMS",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 12.dp).clickable { showSms = !showSms }.padding(vertical = 4.dp),
                )
                if (showSms) Text(txn.smsBody, style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }

            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                text = if (isNew) "Add" else "Save",
                enabled = valid,
                onClick = {
                    if (isNew) {
                        vm.addManual(paise!!, direction, merchant.trim().ifBlank { if (method == "Cash") "Cash" else "Manual entry" }, categoryId, note.trim(), timestamp, method)
                    } else {
                        vm.save(
                            txn!!.copy(
                                amountPaise = paise!!, direction = direction, merchantRaw = merchant.ifBlank { txn.merchantRaw },
                                note = note.trim(), categoryId = categoryId, timestamp = timestamp,
                            ),
                        )
                    }
                    onClose()
                },
            )
            if (!isNew) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { showSplit = true }, enabled = txn!!.amountPaise > 1) { Text("Split", color = c.ink2) }
                    TextButton(onClick = { onDelete(txn!!); onClose() }) { Text("Delete", color = c.over) }
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = utcDate(timestamp))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { timestamp = withLocalTime(it, timestamp) }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }

    if (showSplit && txn != null) {
        SplitDialog(txn, grouped, onDismiss = { showSplit = false }) { p, cat, n ->
            vm.split(txn, p, cat, n)
            showSplit = false
            onClose()
        }
    }
}

@Composable
private fun SplitDialog(
    txn: Txn,
    grouped: List<Pair<CategoryGroup, List<Category>>>,
    onDismiss: () -> Unit,
    onSplit: (Long, Long?, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    val p = Money.parsePaise(amount)
    val valid = p != null && p > 0 && p < txn.amountPaise
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("Split ${Money.format(txn.amountPaise)}") },
        text = {
            Column {
                Text(
                    "Move part of this payment to another category, e.g. drinks on a dinner bill.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTheme.colors.ink2,
                )
                PlainField(amount, { amount = it }, "Amount to move", keyboardType = KeyboardType.Decimal, isError = amount.isNotEmpty() && !valid)
                PlainField(note, { note = it }, "Note")
                CategoryList(grouped, categoryId, { categoryId = it.id }, Modifier.heightIn(max = 220.dp))
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSplit(p!!, categoryId, note.trim()) }) { Text("Split") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** DatePicker works in UTC midnight; convert to and from the local wall-clock date. */
private fun utcDate(millis: Long): Long =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun withLocalTime(utcMidnight: Long, keepTimeFrom: Long): Long {
    val date = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
    val time: LocalTime = Instant.ofEpochMilli(keepTimeFrom).atZone(ZoneId.systemDefault()).toLocalTime()
    return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
