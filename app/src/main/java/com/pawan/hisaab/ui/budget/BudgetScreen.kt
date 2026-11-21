package com.pawan.hisaab.ui.budget

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pawan.hisaab.data.CategoryLine
import com.pawan.hisaab.data.MonthReport
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.ui.components.Meter
import com.pawan.hisaab.ui.components.MoneyText
import com.pawan.hisaab.ui.components.PlainField
import com.pawan.hisaab.ui.components.SecondaryButton
import com.pawan.hisaab.ui.components.TextTabs
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.Numbers
import com.pawan.hisaab.util.Money

private val KINDS = listOf(GroupKind.EXPENSE to "Expenses", GroupKind.INCOME to "Income", GroupKind.SAVINGS to "Savings")

/** Planned vs actual, one kind at a time. Same numbers as the Google Sheet, less noise. */
@Composable
fun BudgetScreen(
    report: MonthReport,
    onSetBudget: (categoryId: Long, paise: Long) -> Unit,
    onCopyPrevious: () -> Unit,
    onExport: () -> Unit,
    contentPadding: PaddingValues,
) {
    val c = FinTheme.colors
    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<CategoryLine?>(null) }
    var showEmpty by remember { mutableStateOf(false) }
    val kind = KINDS[tab].first
    val groups = report.groupsOf(kind).map { g ->
        g to if (showEmpty) g.lines else g.lines.filter { it.actual != 0L || it.projected != 0L }
    }.filter { it.second.isNotEmpty() }
    val hiddenCount = report.groupsOf(kind).sumOf { g -> g.lines.count { it.actual == 0L && it.projected == 0L } }
    val hasBudget = report.groups.any { g -> g.lines.any { it.projected != 0L } }

    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        item { Summary(report) }
        if (!hasBudget) {
            item {
                Text(
                    "No plan for this month yet. Tap a category to set one, or start from last month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.ink2,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
                )
                SecondaryButton("Copy last month's plan", onCopyPrevious)
            }
        }
        item {
            TextTabs(KINDS.map { it.second }, tab, { tab = it }, Modifier.padding(top = 26.dp, bottom = 4.dp))
        }

        if (groups.isEmpty()) {
            item {
                Text("Nothing here yet this month.", style = MaterialTheme.typography.bodyMedium, color = c.ink2, modifier = Modifier.padding(top = 24.dp))
            }
        }
        groups.forEach { (g, lines) ->
            item(key = "g${g.group.id}") {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(g.group.name, style = MaterialTheme.typography.labelMedium, color = c.ink2, modifier = Modifier.weight(1f))
                    Text(
                        Money.format(g.actual) + if (g.projected > 0) " / ${Money.format(g.projected)}" else "",
                        style = MaterialTheme.typography.labelMedium.merge(Numbers),
                        color = c.ink2,
                    )
                }
            }
            items(lines, key = { "l${it.category.id}" }) { line -> LineRow(line, kind) { editing = line } }
        }
        if (kind == GroupKind.EXPENSE && report.uncategorizedDebit > 0) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Not categorized yet", style = MaterialTheme.typography.bodyLarge, color = c.ink2, modifier = Modifier.weight(1f))
                    MoneyText(report.uncategorizedDebit, color = c.ink2)
                }
            }
        }
        if (hiddenCount > 0 || showEmpty) {
            item {
                Text(
                    if (showEmpty) "Hide empty categories" else "Show $hiddenCount more categories to plan",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.padding(top = 20.dp).clickable { showEmpty = !showEmpty }.padding(vertical = 4.dp),
                )
            }
        }
        item {
            Row(Modifier.padding(top = 32.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasBudget) SecondaryButton("Copy last month's plan", onCopyPrevious)
                SecondaryButton("Export to sheet", onExport)
            }
        }
    }

    editing?.let { line ->
        BudgetDialog(line, onDismiss = { editing = null }) { paise ->
            onSetBudget(line.category.id, paise)
            editing = null
        }
    }
}

@Composable
private fun Summary(r: MonthReport) {
    val c = FinTheme.colors
    val planned = r.projectedExpense > 0
    val left = r.projectedExpense - r.expense
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            when {
                !planned -> "Spent"
                left >= 0 -> "Left to spend"
                else -> "Over budget"
            },
            style = MaterialTheme.typography.labelMedium,
            color = c.ink2,
        )
        MoneyText(
            if (planned) kotlin.math.abs(left) else r.expense,
            style = MaterialTheme.typography.displayMedium,
            color = if (planned && left < 0) c.over else c.ink,
        )
        if (planned) {
            Text(
                "${Money.format(r.expense)} spent of ${Money.format(r.projectedExpense)} planned",
                style = MaterialTheme.typography.bodyMedium,
                color = c.ink2,
            )
        }
        Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            MiniStat("Income", r.income, r.projectedIncome)
            MiniStat("Saved", r.savings, r.projectedSavings)
            MiniStat("Net", r.net, null)
        }
    }
}

@Composable
private fun MiniStat(label: String, actual: Long, planned: Long?) {
    val c = FinTheme.colors
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink2)
        Text(Money.format(actual), style = MaterialTheme.typography.titleMedium.merge(Numbers))
        if (planned != null && planned > 0) {
            Text("of ${Money.format(planned)}", style = MaterialTheme.typography.bodySmall.merge(Numbers), color = c.ink3)
        }
    }
}

@Composable
private fun LineRow(line: CategoryLine, kind: GroupKind, onEdit: () -> Unit) {
    val c = FinTheme.colors
    val over = kind == GroupKind.EXPENSE && line.projected > 0 && line.actual > line.projected
    val short = kind != GroupKind.EXPENSE && line.projected > 0 && line.actual < line.projected
    Column(Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(vertical = 12.dp)) {
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
        Row(Modifier.padding(top = 2.dp)) {
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    line.projected == 0L -> "Set plan"
                    over -> "${Money.format(line.actual - line.projected)} over"
                    short -> "${Money.format(line.projected - line.actual)} to go"
                    kind == GroupKind.EXPENSE -> "${Money.format(line.projected - line.actual)} left of ${Money.format(line.projected)}"
                    else -> "of ${Money.format(line.projected)}"
                },
                style = MaterialTheme.typography.bodySmall.merge(Numbers),
                color = when {
                    line.projected == 0L -> c.ink3
                    over -> c.over
                    else -> c.ink3
                },
            )
        }
        if (line.projected > 0) {
            Spacer(Modifier.height(8.dp))
            Meter(line.actual.toFloat() / line.projected, if (over) c.over else c.ink)
        }
    }
}

@Composable
private fun BudgetDialog(line: CategoryLine, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by remember { mutableStateOf(if (line.projected == 0L) "" else Money.plain(line.projected).removeSuffix(".00")) }
    val paise = if (text.isBlank()) 0L else Money.parsePaise(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        title = { Text("${line.category.emoji}  ${line.category.name}") },
        text = {
            Column {
                Text(
                    "${Money.format(line.actual)} so far, ${line.count} payment${if (line.count == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FinTheme.colors.ink2,
                )
                PlainField(text, { text = it }, "Plan for the month (₹)", keyboardType = KeyboardType.Decimal, isError = paise == null)
            }
        },
        confirmButton = { TextButton(enabled = paise != null, onClick = { onSave(paise!!) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
