package com.pawan.hisaab.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.Bill
import com.pawan.hisaab.data.Sharing
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.components.CategoryList
import com.pawan.hisaab.ui.components.TextTabs
import com.pawan.hisaab.ui.components.groupedForDirection
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money

enum class TxnFilter(val label: String) { All("All"), Uncategorized("To sort"), Spent("Spent"), Received("Received"), Shared("Shared") }

@Composable
fun TransactionsScreen(
    txns: List<Txn>,
    categories: List<Category>,
    categoryGroups: List<CategoryGroup>,
    filter: TxnFilter,
    onFilter: (TxnFilter) -> Unit,
    onOpen: (Txn) -> Unit,
    onDelete: (Txn) -> Unit,
    onFileAll: (List<Txn>, Category) -> Unit,
    contentPadding: PaddingValues,
    bills: Map<Long, Bill> = emptyMap(),
) {
    val c = FinTheme.colors
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    var filing by remember { mutableStateOf<TxnGroup?>(null) }
    val catById = remember(categories) { categories.associateBy { it.id } }
    val shown = remember(txns, filter, query, catById, bills) {
        val q = query.trim().lowercase()
        txns.filter { t ->
            when (filter) {
                TxnFilter.All -> true
                TxnFilter.Uncategorized -> t.toSort
                TxnFilter.Spent -> t.direction == Direction.DEBIT
                TxnFilter.Received -> t.direction == Direction.CREDIT
                TxnFilter.Shared -> t.paybackForId != null || t.sharePaise != null || t.id in bills
            } && (
                q.isEmpty() || t.merchantRaw.lowercase().contains(q) || MerchantNormalizer.key(t.merchantRaw).contains(q) ||
                    t.note.lowercase().contains(q) || t.bank.lowercase().contains(q) ||
                    catById[t.categoryId]?.name?.lowercase()?.contains(q) == true
                )
        }
    }
    val groups = remember(shown) { groupByMerchant(shown) }
    val uncategorized = txns.count { it.toSort }
    // What it cost you: shared bills count your share, not the whole bill.
    val spent = shown.filter { it.direction == Direction.DEBIT }.sumOf { Sharing.cost(it, bills[it.id]?.received ?: 0L) }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item {
            SearchBox(query, { query = it }, Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            TextTabs(
                options = TxnFilter.entries.map { if (it == TxnFilter.Uncategorized && uncategorized > 0) "${it.label} $uncategorized" else it.label },
                selected = filter.ordinal,
                onSelect = { onFilter(TxnFilter.entries[it]) },
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (shown.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp)) {
                    Text(
                        "${shown.size} payment${if (shown.size == 1) "" else "s"} · ${groups.size} merchant${if (groups.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = c.ink2,
                    )
                    Spacer(Modifier.weight(1f))
                    if (spent > 0) Text(Money.format(spent), style = MaterialTheme.typography.labelMedium.merge(Numbers), color = c.ink2)
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    if (txns.isEmpty()) "No payments this month yet." else "Nothing here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                    modifier = Modifier.padding(24.dp),
                )
            }
        }
        txnGroupItems(
            groups = groups,
            catById = catById,
            expanded = expanded,
            onToggle = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
            onOpen = onOpen,
            onDelete = onDelete,
            onFileAll = { filing = it },
            horizontalPadding = 24.dp,
            bills = bills,
        )
        item { Spacer(Modifier.size(88.dp)) } // room above the add button
    }

    filing?.let { g ->
        val options = remember(categoryGroups, categories, g) { groupedForDirection(categoryGroups, categories, g.latest.direction) }
        AlertDialog(
            onDismissRequest = { filing = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("File all ${g.txns.size} under…") },
            text = {
                CategoryList(options, selectedId = null, onPick = { cat ->
                    onFileAll(g.txns, cat)
                    filing = null
                }, modifier = Modifier.heightIn(max = 420.dp))
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { filing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = FinTheme.colors
    Row(
        modifier.fillMaxWidth().background(c.tint, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = c.ink3, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search", style = MaterialTheme.typography.bodyLarge, color = c.ink3)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
