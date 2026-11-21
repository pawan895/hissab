package com.pawan.hisaab.ui.category

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.Bill
import com.pawan.hisaab.data.MonthReport
import com.pawan.hisaab.data.MonthSpend
import com.pawan.hisaab.data.Sharing
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.components.Meter
import com.pawan.hisaab.ui.components.MoneyText
import com.pawan.hisaab.ui.components.MonthBars
import com.pawan.hisaab.ui.components.RankedBar
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.dashboard.wholeRupees
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.transactions.groupByMerchant
import com.pawan.hisaab.ui.transactions.txnGroupItems
import com.pawan.hisaab.util.Money
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class MerchantTotal(val name: String, val amount: Long, val count: Int)

/** One category up close: this month, the last six, where the money went, every payment. */
@Composable
fun CategoryScreen(
    category: Category,
    report: MonthReport,
    history: List<MonthSpend>,
    txns: List<Txn>,
    categories: List<Category>,
    bills: Map<Long, Bill>,
    onOpen: (Txn) -> Unit,
    onMonth: (YearMonth) -> Unit,
    contentPadding: PaddingValues,
) {
    val c = FinTheme.colors
    val line = report.groups.flatMap { it.lines }.firstOrNull { it.category.id == category.id }
    val actual = line?.actual ?: 0L
    val projected = line?.projected ?: 0L
    val prev = report.prevActualByCategory[category.id] ?: 0L
    val prevMonth = report.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
    val mine = remember(txns, category.id) { txns.filter { it.categoryId == category.id } }
    val catById = remember(categories) { categories.associateBy { it.id } }
    val groups = remember(mine) { groupByMerchant(mine) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val merchants = remember(mine, bills) {
        mine.filter { it.direction == Direction.DEBIT }
            .groupBy { it.merchantKey }
            .map { (_, ts) ->
                MerchantTotal(
                    MerchantNormalizer.displayName(ts.first().merchantRaw),
                    ts.sumOf { Sharing.cost(it, bills[it.id]?.received ?: 0L) },
                    ts.size,
                )
            }
            .sortedByDescending { it.amount }
            .take(5)
    }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text(
                    "${category.emoji}  Spent in ${report.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = c.ink2,
                )
                MoneyText(actual, style = MaterialTheme.typography.displayLarge)
                val share = if (report.expense > 0) Math.round(actual * 100.0 / report.expense) else 0
                Text(
                    listOfNotNull(
                        "${line?.count ?: 0} payment${if (line?.count == 1) "" else "s"}",
                        if (share > 0) "$share% of your spending" else null,
                        when {
                            prev == 0L -> null
                            actual > prev -> "↑ ${Money.format(actual - prev)} vs $prevMonth"
                            actual < prev -> "↓ ${Money.format(prev - actual)} vs $prevMonth"
                            else -> "same as $prevMonth"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (projected > 0) {
                    val over = actual > projected
                    Text(
                        if (over) "${Money.format(actual - projected)} over the ${Money.format(projected)} budget"
                        else "${Money.format(projected - actual)} left of ${Money.format(projected)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (over) c.over else c.ink2,
                        modifier = Modifier.padding(top = 14.dp, bottom = 10.dp),
                    )
                    Meter(actual.toFloat() / projected, if (over) c.over else c.ink)
                }
            }
        }

        val values = history.map { it.byCategory[category.id] ?: 0L }
        if (values.count { it > 0 } > 1) {
            item {
                val done = history.indices.filter { values[it] > 0 && !(history[it].month == YearMonth.now()) }
                val avg = if (done.isEmpty()) null else wholeRupees(done.sumOf { values[it] } / done.size)
                Column {
                    SectionLabel("Last 6 months")
                    if (avg != null) {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 14.dp)) {
                            MoneyText(avg, style = MaterialTheme.typography.titleMedium)
                            Text(" a month on average", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                        }
                    }
                    MonthBars(
                        values = values.map { it.coerceAtLeast(0) },
                        labels = history.map { it.month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) },
                        highlighted = history.indexOfFirst { it.month == report.month },
                        onSelect = { onMonth(history[it].month) },
                    )
                }
            }
        }

        if (merchants.size > 1) {
            item { SectionLabel("Where") }
            val max = merchants.first().amount.coerceAtLeast(1).toFloat()
            items(merchants, key = { "m${it.name}" }) { m ->
                Column(Modifier.padding(vertical = 9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${m.count}×  ", style = MaterialTheme.typography.bodySmall, color = c.ink3)
                        MoneyText(m.amount)
                    }
                    Spacer(Modifier.height(7.dp))
                    RankedBar(m.amount / max, c.ink)
                }
            }
        }

        item { SectionLabel("Payments") }
        if (mine.isEmpty()) {
            item { Text("Nothing in ${category.name} this month.", style = MaterialTheme.typography.bodyMedium, color = c.ink2) }
        }
        txnGroupItems(
            groups = groups,
            catById = catById,
            expanded = expanded,
            onToggle = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
            onOpen = onOpen,
            onDelete = null,
            onFileAll = null,
            horizontalPadding = 0.dp,
            bills = bills,
        )
        item { Spacer(Modifier.height(40.dp)) }
    }
}
