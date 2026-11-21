package com.pawan.hisaab.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.Bill
import com.pawan.hisaab.data.MonthReport
import com.pawan.hisaab.data.MonthSpend
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.ui.components.DailyBars
import com.pawan.hisaab.ui.components.Donut
import com.pawan.hisaab.ui.components.DonutSlice
import com.pawan.hisaab.ui.components.Dot
import com.pawan.hisaab.ui.components.LineKey
import com.pawan.hisaab.ui.components.Meter
import com.pawan.hisaab.ui.components.MoneyText
import com.pawan.hisaab.ui.components.MonthBars
import com.pawan.hisaab.ui.components.PaceChart
import com.pawan.hisaab.ui.components.SecondaryButton
import com.pawan.hisaab.ui.components.SectionLabel
import com.pawan.hisaab.ui.components.TextTabs
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.ui.transactions.groupByMerchant
import com.pawan.hisaab.ui.transactions.txnGroupItems
import com.pawan.hisaab.util.Money
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun DashboardScreen(
    report: MonthReport,
    history: List<MonthSpend>,
    txns: List<Txn>,
    categories: List<Category>,
    bills: Map<Long, Bill>,
    owed: List<Bill>,
    uncategorizedTotal: Int,
    onCategorizeClick: () -> Unit,
    onSeeAll: () -> Unit,
    onBudget: () -> Unit,
    onCategory: (Category) -> Unit,
    onShared: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onOpen: (Txn) -> Unit,
    onScanInbox: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = FinTheme.colors
    val catById = remember(categories) { categories.associateBy { it.id } }
    val recent = remember(txns) { groupByMerchant(txns).take(5) }
    var expanded by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        contentPadding = contentPadding,
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
    ) {
        item { Hero(report) }

        if (owed.isNotEmpty()) item { OwedCard(owed, onShared) }

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

        if (txns.isEmpty()) {
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
            if (history.any { it.total > 0 }) item { MonthsSection(history, report.month, onMonth) }
            return@LazyColumn
        }

        if (report.topExpenseCategories.isNotEmpty() || report.uncategorizedDebit > 0) {
            item { WhereItWent(report, onCategory, onBudget, onCategorizeClick) }
        }
        item { TrendSection(report) }
        if (history.count { it.total > 0 } > 1) item { MonthsSection(history, report.month, onMonth) }

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
            bills = bills,
        )
        item { Spacer(Modifier.height(88.dp)) }
    }
}

/** Averages read better without paise. */
fun wholeRupees(paise: Long): Long = (paise + 50) / 100 * 100

private fun YearMonth.short() = month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

private fun todayIndex(month: YearMonth): Int? {
    val now = LocalDate.now()
    return if (YearMonth.from(now) == month) now.dayOfMonth - 1 else null
}

@Composable
private fun Hero(r: MonthReport) {
    val c = FinTheme.colors
    val today = todayIndex(r.month)
    Column(Modifier.padding(top = 8.dp)) {
        Text("Spent", style = MaterialTheme.typography.labelMedium, color = c.ink2)
        MoneyText(r.expense, style = MaterialTheme.typography.displayLarge)
        if (r.projectedExpense > 0) {
            val over = r.expense > r.projectedExpense
            val left = r.projectedExpense - r.expense
            val daysLeft = today?.let { r.dailySpend.size - it } ?: 0
            Text(
                when {
                    over -> "${Money.format(-left)} over your ${Money.format(r.projectedExpense)} budget"
                    daysLeft > 1 -> "${Money.format(left)} left · ${Money.format(wholeRupees(left / daysLeft))} a day for $daysLeft days"
                    else -> "${Money.format(left)} left of ${Money.format(r.projectedExpense)}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (over) c.over else c.ink2,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Meter(r.expense.toFloat() / r.projectedExpense, if (over) c.over else c.ink)
        }
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            if (today != null) {
                Stat("Today", r.dailySpend[today])
                Stat("Last 7 days", lastDays(r, today, 7))
                Stat("Income", r.income)
            } else {
                Stat("Income", r.income)
                if (r.savings != 0L) Stat("Saved", r.savings)
                Stat("Net", r.net, signed = true)
            }
        }
        if (r.paidBack > 0) {
            Text(
                "↩ ${Money.format(r.paidBack)} paid back by friends, taken off your bills",
                style = MaterialTheme.typography.bodySmall,
                color = c.ink2,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** Spending over the last [n] days up to [today], reaching back into last month if needed. */
private fun lastDays(r: MonthReport, today: Int, n: Int): Long {
    val here = r.dailySpend.subList((today - n + 1).coerceAtLeast(0), today + 1).sum()
    val fromPrev = (n - (today + 1)).coerceAtLeast(0)
    return here + r.prevDailySpend.takeLast(fromPrev).sum()
}

@Composable
private fun Stat(label: String, value: Long, signed: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = FinTheme.colors.ink2)
        Text(Money.format(value, signed = signed), style = MaterialTheme.typography.titleMedium.merge(Numbers))
    }
}

@Composable
private fun OwedCard(owed: List<Bill>, onClick: () -> Unit) {
    val c = FinTheme.colors
    val total = owed.sumOf { it.outstanding }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Friends owe you", style = MaterialTheme.typography.labelMedium, color = c.ink2)
            Text(
                "${owed.size} shared bill${if (owed.size == 1) "" else "s"} · " +
                    owed.take(2).joinToString(", ") { MerchantNormalizer.displayName(it.txn.merchantRaw) },
                style = MaterialTheme.typography.bodySmall,
                color = c.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MoneyText(total, style = MaterialTheme.typography.titleLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.ink3)
    }
}

private data class Part(val label: String, val emoji: String, val amount: Long, val color: Color, val category: Category?, val prev: Long?, val over: Long)

/** Top five categories in colour, the rest folded into "Everything else", and unsorted payments. */
@Composable
private fun WhereItWent(r: MonthReport, onCategory: (Category) -> Unit, onBudget: () -> Unit, onToSort: () -> Unit) {
    val c = FinTheme.colors
    val prevMonth = r.month.minusMonths(1).short()
    // Nothing to compare with when last month has no sorted spending at all.
    val compare = r.prevActualByCategory.values.any { it != 0L }
    val parts = remember(r, c) {
        val top = r.topExpenseCategories
        val shown = top.take(c.series.size)
        val rest = top.drop(c.series.size)
        buildList {
            shown.forEachIndexed { i, l ->
                add(
                    Part(
                        l.category.name, l.category.emoji, l.actual, c.series[i], l.category,
                        if (compare) r.prevActualByCategory[l.category.id] ?: 0L else null,
                        if (l.projected > 0) (l.actual - l.projected).coerceAtLeast(0) else 0,
                    ),
                )
            }
            if (rest.isNotEmpty()) add(Part("Everything else", "•••", rest.sumOf { it.actual }, c.seriesOther, null, null, 0))
            if (r.uncategorizedDebit > 0) add(Part("Not sorted yet", "?", r.uncategorizedDebit, c.ink3.copy(alpha = 0.3f), null, null, 0))
        }
    }
    val total = parts.sumOf { it.amount }.coerceAtLeast(1)
    var selected by remember(r.month) { mutableStateOf<Int?>(null) }

    Column {
        SectionLabel("Where it went", action = "All categories", onAction = onBudget)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 10.dp), horizontalArrangement = Arrangement.Center) {
            Donut(
                slices = parts.map { DonutSlice(it.amount, it.color) },
                selected = selected,
                onSelect = { selected = it },
            ) {
                val sel = selected?.let { parts.getOrNull(it) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 112.dp)) {
                    if (sel != null) {
                        Text(sel.emoji, style = MaterialTheme.typography.titleMedium)
                        MoneyText(sel.amount, style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${percent(sel.amount, total)} · ${sel.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = c.ink2,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } else {
                        // The total is already the big number above; the centre invites a tap instead.
                        val n = r.topExpenseCategories.size
                        Text("$n", style = MaterialTheme.typography.titleLarge.merge(Numbers))
                        Text(if (n == 1) "category" else "categories", style = MaterialTheme.typography.labelSmall, color = c.ink2)
                        Text("tap a slice", style = MaterialTheme.typography.labelSmall, color = c.ink3)
                    }
                }
            }
        }
        // The list is the legend: colour, name, share and amount on every row.
        parts.forEachIndexed { i, p ->
            val on = selected == i
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) c.tint else Color.Transparent)
                    .clickable {
                        when {
                            p.category != null -> onCategory(p.category)
                            p.label == "Not sorted yet" -> onToSort()
                            else -> onBudget()
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Dot(p.color)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (p.category != null) "${p.emoji}  ${p.label}" else p.label,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val note = when {
                        p.over > 0 -> "${Money.format(p.over)} over budget"
                        p.prev == null -> null
                        p.prev == 0L -> "New this month"
                        p.amount > p.prev -> "↑ ${Money.format(p.amount - p.prev)} vs $prevMonth"
                        p.amount < p.prev -> "↓ ${Money.format(p.prev - p.amount)} vs $prevMonth"
                        else -> "Same as $prevMonth"
                    }
                    if (note != null) {
                        Text(note, style = MaterialTheme.typography.bodySmall, color = if (p.over > 0) c.over else c.ink3)
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(p.amount)
                    Text(percent(p.amount, total), style = MaterialTheme.typography.labelSmall.merge(Numbers), color = c.ink3)
                }
            }
        }
    }
}

private fun percent(part: Long, total: Long): String {
    val p = part * 100.0 / total
    return if (p in 0.0..0.5 && part > 0) "<1%" else "${Math.round(p)}%"
}

@Composable
private fun TrendSection(r: MonthReport) {
    val c = FinTheme.colors
    var mode by remember { mutableIntStateOf(0) }
    var selected by remember(r.month, mode) { mutableStateOf<Int?>(null) }
    val today = todayIndex(r.month)
    val upTo = today ?: r.dailySpend.lastIndex
    val month = r.month.short()
    val prevMonth = r.month.minusMonths(1).short()
    val current = remember(r) { r.dailySpend.take(upTo + 1).runningReduce { a, b -> a + b } }
    val previous = remember(r) { if (r.prevDailySpend.isEmpty()) emptyList() else r.prevDailySpend.runningReduce { a, b -> a + b } }
    val hasPrev = previous.lastOrNull()?.let { it > 0 } == true

    Column {
        SectionLabel("Spending")
        TextTabs(listOf("Running total", "Day by day"), mode, { mode = it }, Modifier.padding(bottom = 10.dp))

        val sel = selected
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 14.dp)) {
            if (mode == 0) {
                if (sel != null) {
                    Text("By ${sel + 1} $month  ", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    current.getOrNull(sel)?.let { MoneyText(it, style = MaterialTheme.typography.titleMedium) }
                    if (hasPrev) {
                        Text(
                            "  ·  $prevMonth ${previous.getOrNull(sel)?.let(Money::format) ?: "–"}",
                            style = MaterialTheme.typography.bodyMedium.merge(Numbers),
                            color = c.ink2,
                        )
                    }
                } else if (hasPrev) {
                    val now = current.lastOrNull() ?: 0L
                    val then = if (today != null) previous.getOrNull(today) ?: previous.last() else previous.last()
                    val diff = now - then
                    MoneyText(kotlin.math.abs(diff), style = MaterialTheme.typography.titleMedium)
                    Text(
                        " " + (if (diff >= 0) "more" else "less") +
                            (if (today != null) " than by this day in $prevMonth" else " than in $prevMonth"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.ink2,
                    )
                } else {
                    Text("Tap or drag to see any day", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
            } else {
                val days = upTo + 1
                val avg = wholeRupees(r.dailySpend.take(days).sum() / days.coerceAtLeast(1))
                if (sel != null) {
                    Text("${sel + 1} $month  ", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                    MoneyText(r.dailySpend[sel], style = MaterialTheme.typography.titleMedium)
                } else {
                    MoneyText(avg, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(6.dp))
                    Text("a day on average", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
                }
            }
        }

        if (mode == 0) {
            val budget = r.projectedExpense.takeIf { it > 0 }
            PaceChart(
                current = current,
                previous = if (hasPrev) previous else emptyList(),
                days = r.dailySpend.size,
                budget = budget,
                selected = selected,
                onSelect = { selected = it },
            )
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Key(c.ink, false, month)
                if (hasPrev) Key(c.ink3, true, prevMonth)
                if (budget != null) Key(c.over.copy(alpha = 0.7f), true, "Budget")
            }
        } else {
            val days = upTo + 1
            DailyBars(
                values = r.dailySpend,
                selected = selected,
                today = today,
                onSelect = { selected = it },
                average = r.dailySpend.take(days).sum() / days.coerceAtLeast(1),
            )
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Key(c.ink2, true, "Daily average")
            }
        }
    }
}

@Composable
private fun Key(color: Color, dashed: Boolean, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        LineKey(color, dashed)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = FinTheme.colors.ink2)
    }
}

@Composable
private fun MonthsSection(history: List<MonthSpend>, month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val c = FinTheme.colors
    val inProgress = YearMonth.now() == month
    // Average of complete months that had any spending.
    val done = history.filter { it.total > 0 && !(inProgress && it.month == month) }
    val avg = if (done.isEmpty()) null else wholeRupees(done.sumOf { it.total } / done.size)
    Column {
        SectionLabel("Last 6 months")
        if (avg != null) {
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 14.dp)) {
                MoneyText(avg, style = MaterialTheme.typography.titleMedium)
                Text(" a month on average", style = MaterialTheme.typography.bodyMedium, color = c.ink2)
            }
        }
        MonthBars(
            values = history.map { it.total },
            labels = history.map { it.month.short() },
            highlighted = history.indexOfFirst { it.month == month },
            onSelect = { i -> onMonth(history[i].month) },
        )
        Text(
            "Tap a month to open it",
            style = MaterialTheme.typography.labelSmall,
            color = c.ink3,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
