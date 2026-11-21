package com.pawan.hisaab.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.pawan.hisaab.data.MonthReport
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.ui.components.DailyBars
import com.pawan.hisaab.ui.components.Meter
import com.pawan.hisaab.ui.components.MoneyText
import com.pawan.hisaab.ui.components.RankedBar
import com.pawan.hisaab.ui.components.SecondaryButton
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.transactions.groupByMerchant
import com.pawan.hisaab.ui.transactions.txnGroupItems
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    report: MonthReport,
    txns: List<Txn>,
    categories: List<Category>,
    uncategorizedTotal: Int,
    onCategorizeClick: () -> Unit,
    onSeeAll: () -> Unit,
    onBudget: () -> Unit,
    onOpen: (Txn) -> Unit,
    onScanInbox: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = FinTheme.colors
    val isEmpty = txns.isEmpty()
    val catById = remember(categories) { categories.associateBy { it.id } }
    val recent = remember(txns) { groupByMerchant(txns).take(5) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    val prevMonth = report.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        item { Hero(report) }

        if (uncategorizedTotal > 0) {
            item {
                Text(
                    if (uncategorizedTotal == 1) "1 payment to categorize  →" else "$uncategorizedTotal payments to categorize  →",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.padding(top = 20.dp).clickable(onClick = onCategorizeClick).padding(vertical = 4.dp),
                )
            }
        }

        if (isEmpty) {
            item {
                Text(
                    "Nothing yet this month. When a bank SMS arrives you'll be asked what it was for, " +
                        "or pull in payments already in your inbox.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 32.dp, bottom = 14.dp),
                )
                SecondaryButton("Scan SMS inbox", onScanInbox)
            }
            return@LazyColumn
        }

        val top = report.topExpenseCategories.take(5)
        if (top.isNotEmpty()) {
            item { SectionLabel("Top categories", action = "Budget", onAction = onBudget) }
            val max = top.first().actual.toFloat()
            items(top, key = { "c${it.category.id}" }) { line ->
                val over = line.projected > 0 && line.actual > line.projected
                val prev = report.prevActualByCategory[line.category.id] ?: 0L
                Column(Modifier.padding(vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${line.category.emoji}  ${line.category.name}",
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        MoneyText(line.actual)
                    }
                    Spacer(Modifier.height(8.dp))
                    RankedBar(line.actual / max, if (over) c.over else c.ink)
                    Text(
                        when {
                            over -> "${Money.format(line.actual - line.projected)} over budget"
                            prev == 0L -> "${line.count} payment${if (line.count == 1) "" else "s"}"
                            line.actual > prev -> "↑ ${Money.format(line.actual - prev)} vs $prevMonth"
                            line.actual < prev -> "↓ ${Money.format(prev - line.actual)} vs $prevMonth"
                            else -> "Same as $prevMonth"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (over) c.over else c.ink3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }

        item { DailySection(report) }

        item { SectionLabel("Recent", action = "See all", onAction = onSeeAll) }
        // Same merchant grouping as Activity: one row per merchant with its total.
        txnGroupItems(
            groups = recent,
            catById = catById,
            expanded = expanded,
            onToggle = { key -> expanded = if (key in expanded) expanded - key else expanded + key },
            onOpen = onOpen,
            onDelete = null,
            onFileAll = null,
            horizontalPadding = 0.dp,
        )
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun Hero(r: MonthReport) {
    val c = FinTheme.colors
    Column(Modifier.padding(top = 8.dp)) {
        Text("Spent", style = MaterialTheme.typography.labelMedium, color = c.ink2)
        MoneyText(r.expense, style = MaterialTheme.typography.displayLarge)
        if (r.projectedExpense > 0) {
            val over = r.expense > r.projectedExpense
            Text(
                if (over) "${Money.format(r.expense - r.projectedExpense)} over your ${Money.format(r.projectedExpense)} budget"
                else "${Money.format(r.projectedExpense - r.expense)} left of ${Money.format(r.projectedExpense)}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) c.over else c.ink2,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Meter(r.expense.toFloat() / r.projectedExpense, if (over) c.over else c.ink)
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Stat("Income", r.income)
            if (r.savings != 0L) Stat("Saved", r.savings)
            Stat("Net", r.net, signed = true)
        }
    }
}

@Composable
private fun Stat(label: String, value: Long, signed: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinTheme.colors.ink2)
        Text(Money.format(value, signed = signed), style = MaterialTheme.typography.titleMedium.merge(Numbers))
    }
}

@Composable
private fun DailySection(r: MonthReport) {
    val c = FinTheme.colors
    var selected by remember(r.month) { mutableStateOf<Int?>(null) }
    val now = LocalDate.now()
    val today = if (YearMonth.from(now) == r.month) now.dayOfMonth - 1 else null
    val daysSoFar = today?.plus(1) ?: r.dailySpend.size
    val monthShort = r.month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

    Column {
        SectionLabel("Every day")
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 14.dp)) {
            val sel = selected
            if (sel != null) {
                Text("${sel + 1} $monthShort  ", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                MoneyText(r.dailySpend[sel], style = MaterialTheme.typography.titleMedium)
            } else {
                MoneyText(r.dailySpend.sum() / daysSoFar.coerceAtLeast(1), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                Text("a day on average", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            }
        }
        DailyBars(values = r.dailySpend, selected = selected, today = today, onSelect = { selected = it })
    }
}
