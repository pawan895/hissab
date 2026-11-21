package com.pawan.hisaab.data

import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.util.Money
import java.time.Month
import java.time.YearMonth
import java.util.Locale

object Csv {
    /** RFC-4180-ish parser: quoted fields, escaped quotes, newlines inside quotes. */
    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    ch == '"' -> inQuotes = false
                    else -> field.append(ch)
                }
            } else {
                when (ch) {
                    '"' -> inQuotes = true
                    ',' -> { row.add(field.toString()); field.clear() }
                    '\r' -> {}
                    '\n' -> { row.add(field.toString()); field.clear(); rows.add(row); row = mutableListOf() }
                    else -> field.append(ch)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }

    fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    fun line(cells: List<String>): String = cells.joinToString(",") { escape(it) }
}

data class SheetItem(
    val groupName: String,
    val kind: GroupKind,
    val category: String,
    val projected: Long?,
    val actual: Long?,
)

/** Reads and writes the "PERSONAL MONTHLY BUDGET" Google Sheet layout (columns B–E and G–J). */
object SheetIo {
    private const val L = 1 // column B
    private const val R = 6 // column G

    fun parse(text: String): List<SheetItem> {
        val rows = Csv.parse(text)
        fun cell(r: List<String>, i: Int) = r.getOrNull(i)?.trim().orEmpty()
        val items = mutableListOf<SheetItem>()

        // Left block: INCOME and SAVINGS sections, until BUDGET SUMMARY.
        var section: GroupKind? = null
        for (r in rows) {
            val name = cell(r, L)
            when (name.uppercase(Locale.ROOT)) {
                "" , "TOTAL", "PERSONAL MONTHLY BUDGET" -> continue
                "INCOME" -> { section = GroupKind.INCOME; continue }
                "SAVINGS" -> { section = GroupKind.SAVINGS; continue }
                "BUDGET SUMMARY" -> break
            }
            val kind = section ?: continue
            items += SheetItem(
                groupName = if (kind == GroupKind.INCOME) "Income" else "Savings",
                kind = kind,
                category = name,
                projected = Money.parsePaise(cell(r, L + 1)),
                actual = Money.parsePaise(cell(r, L + 2)),
            )
        }

        // Right block: expense groups. A group header has a name but no numbers at all.
        var group: String? = null
        for (r in rows) {
            val name = cell(r, R)
            if (name.isEmpty() || name.equals("EXPENSES", true)) continue
            if (name.equals("TOTAL", true)) break
            val p = cell(r, R + 1)
            val a = cell(r, R + 2)
            val d = cell(r, R + 3)
            if (p.isEmpty() && a.isEmpty() && d.isEmpty()) {
                group = titleCase(name)
                continue
            }
            val g = group ?: continue
            items += SheetItem(g, GroupKind.EXPENSE, name, Money.parsePaise(p), Money.parsePaise(a))
        }
        return items
    }

    /** "Monthly Budget - October.csv" -> 2026-10 (latest October not in the future). */
    fun guessMonth(fileName: String?, today: YearMonth = YearMonth.now()): YearMonth {
        val lower = fileName?.lowercase(Locale.ROOT).orEmpty()
        val month = Month.entries.firstOrNull { m ->
            val full = m.name.lowercase(Locale.ROOT)
            lower.contains(full) || Regex("""\b${full.take(3)}\b""").containsMatchIn(lower)
        } ?: return today
        val year = Regex("""\b(20\d{2})\b""").find(lower)?.value?.toInt()
            ?: if (month.value > today.monthValue) today.year - 1 else today.year
        return YearMonth.of(year, month)
    }

    private fun titleCase(s: String): String =
        if (s.any { it.isLowerCase() }) s
        else s.lowercase(Locale.ROOT).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    /** Exports a month in the same two-block layout as the sheet so it can be pasted straight back. */
    fun export(report: MonthReport): String {
        fun n(v: Long) = Money.plain(v)
        val left = mutableListOf<List<String>>()
        val right = mutableListOf<List<String>>()

        left += listOf("PERSONAL MONTHLY BUDGET", "", "", "")
        right += listOf(report.month.label(), "", "", "")
        left += listOf("", "PROJECTED", "ACTUAL", "DIFFERENCE")
        right += listOf("", "PROJECTED", "ACTUAL", "DIFFERENCE")

        fun section(title: String, kind: GroupKind) {
            left += listOf(title, "", "", "")
            val lines = report.groupsOf(kind).flatMap { it.lines }
            lines.forEach { left += listOf(it.category.name, n(it.projected), n(it.actual), n(it.difference)) }
            val p = lines.sumOf { it.projected }
            val a = lines.sumOf { it.actual }
            left += listOf("TOTAL", n(p), n(a), n(a - p))
            left += listOf("", "", "", "")
        }
        section("INCOME", GroupKind.INCOME)
        section("SAVINGS", GroupKind.SAVINGS)

        val pOut = report.projectedExpense + report.projectedSavings
        val aOut = report.expense + report.savings
        left += listOf("BUDGET SUMMARY", "PROJECTED", "ACTUAL", "DIFFERENCE")
        left += listOf("TOTAL INCOME", n(report.projectedIncome), n(report.income), n(report.income - report.projectedIncome))
        left += listOf("TOTAL EXPENSES", n(pOut), n(aOut), n(aOut - pOut))
        left += listOf("NET", n(report.projectedNet), n(report.net), n(report.net - report.projectedNet))

        right += listOf("EXPENSES", "", "", "")
        for (g in report.groupsOf(GroupKind.EXPENSE)) {
            right += listOf(g.group.name.uppercase(Locale.ROOT), "", "", "")
            g.lines.forEach { right += listOf(it.category.name, n(it.projected), n(it.actual), n(it.difference)) }
            right += listOf("", n(g.projected), n(g.actual), n(g.difference))
        }
        if (report.uncategorizedDebit > 0) {
            right += listOf("UNCATEGORIZED", "", "", "")
            right += listOf("Uncategorized spends", n(0), n(report.uncategorizedDebit), n(report.uncategorizedDebit))
        }
        right += listOf("TOTAL", n(report.projectedExpense), n(report.expense), n(report.expense - report.projectedExpense))

        val blank = listOf("", "", "", "")
        return (0 until maxOf(left.size, right.size)).joinToString("\n") { i ->
            Csv.line(listOf("") + (left.getOrNull(i) ?: blank) + listOf("") + (right.getOrNull(i) ?: blank))
        } + "\n"
    }
}
