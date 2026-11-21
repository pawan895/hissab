package com.pawan.hisaab.popup

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.container
import com.pawan.hisaab.data.FinanceRepository
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import com.pawan.hisaab.data.formatDate
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.components.CategoryList
import com.pawan.hisaab.ui.components.Pill
import com.pawan.hisaab.ui.components.groupedForDirection
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PopupState(
    val txn: Txn,
    val suggestions: List<Category>,
    val learned: Category?,
    val auto: Category?,
    val groups: List<Pair<CategoryGroup, List<Category>>>,
)

private sealed interface Mode {
    data object Ask : Mode
    data object Auto : Mode
    data class Saved(val category: Category) : Mode
}

private suspend fun loadState(repo: FinanceRepository, request: PopupRequest): PopupState? {
    val txn = if (request.preview) sampleTxn() else repo.txn(request.txnId) ?: return null
    val auto = if (request.autoCategorized) txn.categoryId?.let { repo.category(it) } else null
    return PopupState(
        txn = txn,
        suggestions = repo.suggestions(txn, limit = 5),
        learned = repo.learnedCategoryFor(txn),
        auto = auto,
        groups = groupedForDirection(repo.allGroups(), repo.allCategories(), txn.direction),
    )
}

private fun sampleTxn() = Txn(
    id = -1, amountPaise = 20405, direction = Direction.DEBIT, timestamp = System.currentTimeMillis(),
    merchantRaw = "zomatoltd32.rzp@hdfcbank", merchantKey = "zomato", bank = "HDFC", accountLast4 = "1111",
    source = TxnSource.SMS,
)

@Composable
fun PopupCard(request: PopupRequest, onDismiss: () -> Unit, onNeedKeyboard: (Boolean) -> Unit) {
    val repo = LocalContext.current.container.repo
    var state by remember { mutableStateOf<PopupState?>(null) }
    LaunchedEffect(request) {
        val s = withContext(Dispatchers.IO) { loadState(repo, request) }
        if (s == null) onDismiss() else state = s
    }
    state?.let { PopupContent(it, request.preview, repo, onDismiss, onNeedKeyboard) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PopupContent(
    s: PopupState,
    preview: Boolean,
    repo: FinanceRepository,
    onDismiss: () -> Unit,
    onNeedKeyboard: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val c = FinTheme.colors
    val txn = s.txn
    val isDebit = txn.direction == Direction.DEBIT

    var mode by remember { mutableStateOf<Mode>(if (s.auto != null) Mode.Auto else Mode.Ask) }
    var showAll by remember { mutableStateOf(false) }
    var noteOpen by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val focus = remember { FocusRequester() }

    // Auto-dismiss; any touch restarts the timer. The transaction is already saved either way.
    LaunchedEffect(lastTouch, mode) {
        delay(
            when (mode) {
                Mode.Ask -> 30_000L
                Mode.Auto -> 6_000L
                is Mode.Saved -> 1_200L
            },
        )
        onNeedKeyboard(false)
        onDismiss()
    }
    LaunchedEffect(noteOpen) {
        if (noteOpen) {
            onNeedKeyboard(true)
            delay(150)
            runCatching { focus.requestFocus() }
        }
    }

    fun pick(cat: Category) {
        lastTouch = System.currentTimeMillis()
        scope.launch {
            if (!preview) withContext(Dispatchers.IO) { repo.categorize(txn.id, cat.id, note.trim().ifEmpty { null }) }
            onNeedKeyboard(false)
            mode = Mode.Saved(cat)
        }
    }

    fun close() {
        onNeedKeyboard(false)
        onDismiss()
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        lastTouch = System.currentTimeMillis()
                    }
                }
            },
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp).animateContentSize()) {
            Text(
                (if (isDebit) "Paid to " else "Received from ") + MerchantNormalizer.displayName(txn.merchantRaw),
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    (if (isDebit) "" else "+") + Money.format(txn.amountPaise),
                    style = MaterialTheme.typography.displaySmall.merge(Numbers),
                    color = if (isDebit) c.ink else c.credit,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    listOfNotNull(
                        txn.bank.ifBlank { null },
                        txn.accountLast4.ifBlank { null }?.let { "••$it" },
                        if (preview) "preview" else null,
                    ).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.ink3,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            Spacer(Modifier.height(14.dp))
            when (val m = mode) {
                is Mode.Saved -> DoneRow("Saved to ${m.category.emoji} ${m.category.name}", null) {}
                Mode.Auto -> DoneRow("Filed under ${s.auto!!.emoji} ${s.auto.name}", "Change") { mode = Mode.Ask }
                Mode.Ask -> {
                    if (!showAll) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            s.suggestions.forEach { cat ->
                                Pill("${cat.emoji}  ${cat.name}", cat.id == s.learned?.id, { pick(cat) })
                            }
                            Pill("More", false, { showAll = true })
                        }
                    } else {
                        CategoryList(s.groups, s.learned?.id, ::pick, Modifier.heightIn(max = 300.dp))
                    }
                    if (noteOpen) {
                        TextField(
                            value = note,
                            onValueChange = { note = it; lastTouch = System.currentTimeMillis() },
                            placeholder = { Text("Add a note, then pick a category") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onNeedKeyboard(false) }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = c.ink,
                                unfocusedIndicatorColor = c.hairline,
                            ),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!noteOpen) QuietAction("Add note") { noteOpen = true }
                        if (showAll) QuietAction("Back") { showAll = false }
                        Spacer(Modifier.weight(1f))
                        QuietAction("Skip", ::close)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietAction(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = FinTheme.colors.ink2,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp),
    )
}

@Composable
private fun DoneRow(text: String, action: String?, onAction: () -> Unit) {
    val c = FinTheme.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Check, contentDescription = null, tint = c.credit, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(6.dp),
            )
        }
    }
}
