package com.pawan.hisaab.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.Bill
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import com.pawan.hisaab.data.formatDate
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.components.ListRow
import com.pawan.hisaab.ui.components.TxnAmount
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money

/** Payments to one merchant in one direction, newest first. A group of one is shown as a plain row. */
data class TxnGroup(val key: String, val txns: List<Txn>) {
    val latest: Txn get() = txns.first()
    val total: Long get() = txns.sumOf { it.amountPaise }
    val isGroup: Boolean get() = txns.size > 1
}

private val UNGROUPED_KEYS = setOf("unknown", "manual entry", "cash")

/** Keeps the newest-first order of [txns]; each merchant appears where its latest payment is. */
fun groupByMerchant(txns: List<Txn>): List<TxnGroup> {
    val groups = LinkedHashMap<String, MutableList<Txn>>()
    for (t in txns) {
        val key = if (t.source == TxnSource.IMPORT || t.merchantKey in UNGROUPED_KEYS) "id:${t.id}" else "${t.merchantKey}|${t.direction}"
        groups.getOrPut(key) { mutableListOf() }.add(t)
    }
    return groups.map { (k, v) -> TxnGroup(k, v) }
}

private fun title(t: Txn) = if (t.source == TxnSource.IMPORT) t.merchantRaw else MerchantNormalizer.displayName(t.merchantRaw)

/** Needs a category from you: not sorted and not a payback (those belong to their bill). */
val Txn.toSort: Boolean get() = categoryId == null && paybackForId == null

/** "Paid back · Barbeque Nation" for a payback, or the bill's split for a shared bill. */
fun sharingNote(t: Txn, bills: Map<Long, Bill>): String? {
    t.paybackForId?.let { id ->
        return "Paid back" + (bills[id]?.let { " · " + MerchantNormalizer.displayName(it.txn.merchantRaw) } ?: "")
    }
    val bill = bills[t.id] ?: return null
    return when {
        bill.outstanding > 0 -> "your share ${Money.format(bill.cost)} · ${Money.format(bill.outstanding)} due"
        bill.txn.sharePaise != null -> "your share ${Money.format(bill.cost)}"
        bill.received > 0 -> "cost you ${Money.format(bill.cost)}"
        else -> null
    }
}

private const val PAYBACK_EMOJI = "↩"

/**
 * Emits list items for [groups]. Merchant groups show the total and expand on tap to
 * list each payment. [onDelete] enables swipe-to-delete; [onFileAll] adds a
 * "Change category for all" action inside expanded groups.
 */
fun LazyListScope.txnGroupItems(
    groups: List<TxnGroup>,
    catById: Map<Long, Category>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onOpen: (Txn) -> Unit,
    onDelete: ((Txn) -> Unit)?,
    onFileAll: ((TxnGroup) -> Unit)?,
    horizontalPadding: Dp,
    bills: Map<Long, Bill> = emptyMap(),
) {
    groups.forEach { g ->
        if (!g.isGroup) {
            item(key = "t${g.latest.id}") {
                val t = g.latest
                Swipeable(onDelete?.let { { it(t) } }) { SingleRow(t, catById[t.categoryId], bills, horizontalPadding) { onOpen(t) } }
            }
            return@forEach
        }
        val open = g.key in expanded
        item(key = "g${g.key}") { GroupRow(g, catById, bills, open, horizontalPadding) { onToggle(g.key) } }
        if (open) {
            items(g.txns, key = { "c${it.id}" }) { t ->
                Swipeable(onDelete?.let { { it(t) } }) { ChildRow(t, catById[t.categoryId], bills, horizontalPadding) { onOpen(t) } }
            }
            if (onFileAll != null) {
                item(key = "a${g.key}") {
                    Text(
                        "Change category for all ${g.txns.size}",
                        style = MaterialTheme.typography.labelLarge,
                        color = FinTheme.colors.accent,
                        modifier = Modifier
                            .padding(start = horizontalPadding + 54.dp, top = 2.dp, bottom = 10.dp)
                            .clickable { onFileAll(g) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleRow(t: Txn, category: Category?, bills: Map<Long, Bill>, hPad: Dp, onClick: () -> Unit) {
    val c = FinTheme.colors
    val sharing = sharingNote(t, bills)
    val subtitle = listOfNotNull(
        if (t.paybackForId != null) null else category?.name ?: "Categorize",
        sharing,
        t.note.ifBlank { null },
        when (t.source) {
            TxnSource.CASH -> "Cash"
            TxnSource.MANUAL -> "Added by you"
            else -> null
        },
        if (t.needsReview) "Check details" else null,
    ).joinToString(" · ")
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = hPad)) {
        ListRow(
            emoji = if (t.paybackForId != null) PAYBACK_EMOJI else category?.emoji ?: "?",
            title = title(t),
            subtitle = subtitle,
            subtitleColor = if (t.toSort) c.accent else c.ink2,
            onClick = onClick,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                TxnAmount(t)
                Text(t.timestamp.formatDate("d MMM"), style = MaterialTheme.typography.labelSmall, color = c.ink3)
            }
        }
    }
}

@Composable
private fun GroupRow(g: TxnGroup, catById: Map<Long, Category>, bills: Map<Long, Bill>, open: Boolean, hPad: Dp, onClick: () -> Unit) {
    val c = FinTheme.colors
    val cats = g.txns.mapNotNull { it.categoryId?.let(catById::get) }.distinctBy { it.id }
    val unsorted = g.txns.count { it.toSort }
    val paybacks = g.txns.count { it.paybackForId != null }
    val due = g.txns.sumOf { bills[it.id]?.outstanding ?: 0L }
    val categoryText = when {
        cats.isEmpty() -> if (paybacks > 0) "paid back" else null
        cats.size == 1 -> cats.first().name + if (paybacks > 0) " · paid back" else ""
        else -> "${cats.size} categories"
    }
    val credit = g.latest.direction == Direction.CREDIT
    Box(Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = hPad)) {
        ListRow(
            emoji = when {
                cats.size == 1 -> cats.first().emoji
                cats.isEmpty() && paybacks > 0 -> PAYBACK_EMOJI
                cats.isEmpty() -> "?"
                else -> "•••"
            },
            title = title(g.latest),
            subtitle = listOfNotNull(
                "${g.txns.size} payments",
                categoryText,
                if (due > 0) "${Money.format(due)} due" else null,
                if (unsorted > 0) "$unsorted to sort" else null,
            ).joinToString(" · "),
            subtitleColor = if (unsorted > 0) c.accent else c.ink2,
            onClick = onClick,
        ) {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (credit) "+" else "") + Money.format(g.total),
                    style = MaterialTheme.typography.bodyLarge.merge(Numbers),
                    color = if (credit) c.credit else c.ink,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(g.latest.timestamp.formatDate("d MMM"), style = MaterialTheme.typography.labelSmall, color = c.ink3)
                    Icon(
                        if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (open) "Hide payments" else "Show payments",
                        tint = c.ink3,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChildRow(t: Txn, category: Category?, bills: Map<Long, Bill>, hPad: Dp, onClick: () -> Unit) {
    val c = FinTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(start = hPad + 54.dp, end = hPad, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(t.timestamp.formatDate("EEE d MMM, h:mm a"), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(
                    if (t.paybackForId != null) null else category?.let { "${it.emoji} ${it.name}" } ?: "Categorize",
                    sharingNote(t, bills),
                    t.note.ifBlank { null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (t.toSort) c.accent else c.ink2,
            )
        }
        Spacer(Modifier.width(12.dp))
        TxnAmount(t, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Swipeable(onDelete: (() -> Unit)?, content: @Composable () -> Unit) {
    if (onDelete == null) {
        content()
        return
    }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(FinTheme.colors.over).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
        },
    ) { content() }
}
