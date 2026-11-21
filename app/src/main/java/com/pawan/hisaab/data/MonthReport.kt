package com.pawan.hisaab.data

import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.MonthlyBudget
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.sms.MerchantNormalizer
import java.time.YearMonth

data class CategoryLine(val category: Category, val projected: Long, val actual: Long, val count: Int) {
    val difference: Long get() = actual - projected
}

data class GroupLine(val group: CategoryGroup, val lines: List<CategoryLine>) {
    val projected: Long get() = lines.sumOf { it.projected }
    val actual: Long get() = lines.sumOf { it.actual }
    val difference: Long get() = actual - projected
}

data class MerchantSpend(val name: String, val amount: Long, val count: Int)

data class MonthReport(
    val month: YearMonth,
    val groups: List<GroupLine>,
    val uncategorizedDebit: Long,
    val uncategorizedCredit: Long,
    val uncategorizedCount: Int,
    /** Index 0 = day 1. Everyday spending only (no savings, no sheet imports), after paybacks. */
    val dailySpend: List<Long>,
    /** The same for the month before, to compare the pace. */
    val prevDailySpend: List<Long>,
    val topMerchants: List<MerchantSpend>,
    val prevActualByCategory: Map<Long, Long>,
    /** Money friends sent back this month for bills you paid. Not counted as income. */
    val paidBack: Long = 0,
) {
    private fun sum(kind: GroupKind, f: (GroupLine) -> Long) = groups.filter { it.group.kind == kind }.sumOf(f)

    val income: Long get() = sum(GroupKind.INCOME) { it.actual } + uncategorizedCredit
    val expense: Long get() = sum(GroupKind.EXPENSE) { it.actual } + uncategorizedDebit
    val savings: Long get() = sum(GroupKind.SAVINGS) { it.actual }
    val net: Long get() = income - expense - savings

    val projectedIncome: Long get() = sum(GroupKind.INCOME) { it.projected }
    val projectedExpense: Long get() = sum(GroupKind.EXPENSE) { it.projected }
    val projectedSavings: Long get() = sum(GroupKind.SAVINGS) { it.projected }
    val projectedNet: Long get() = projectedIncome - projectedExpense - projectedSavings

    fun groupsOf(kind: GroupKind) = groups.filter { it.group.kind == kind }

    /** Expense categories ordered by how much was spent. */
    val topExpenseCategories: List<CategoryLine>
        get() = groupsOf(GroupKind.EXPENSE).flatMap { it.lines }.filter { it.actual > 0 }.sortedByDescending { it.actual }

    val overBudget: List<CategoryLine>
        get() = groupsOf(GroupKind.EXPENSE).flatMap { it.lines }.filter { it.projected > 0 && it.actual > it.projected }

    companion object {
        /** See [Ledger] for the sign rules and how paybacks lower a bill. */
        fun build(
            month: YearMonth,
            groups: List<CategoryGroup>,
            categories: List<Category>,
            budgets: List<MonthlyBudget>,
            txns: List<Txn>,
            prevTxns: List<Txn>,
            paybacks: List<Txn> = emptyList(),
        ): MonthReport {
            val ledger = Ledger(groups, categories, Ledger.receivedByBill(paybacks))
            val projected = budgets.associate { it.categoryId to it.projectedPaise }

            val actual = HashMap<Long, Long>()
            val counts = HashMap<Long, Int>()
            var uncatDebit = 0L
            var uncatCredit = 0L
            var uncatCount = 0
            var paidBack = 0L
            val merchants = HashMap<String, MerchantSpend>()

            for (t in txns) {
                if (ledger.isPayback(t)) {
                    paidBack += t.amountPaise
                    continue
                }
                val cat = t.categoryId
                if (cat == null || ledger.kindByCategory[cat] == null) {
                    uncatCount++
                    if (t.direction == Direction.DEBIT) uncatDebit += ledger.amount(t) else uncatCredit += t.amountPaise
                } else {
                    actual[cat] = (actual[cat] ?: 0L) + ledger.signed(t)
                    counts[cat] = (counts[cat] ?: 0) + 1
                }
                if (ledger.isEverydaySpend(t)) {
                    val m = merchants[t.merchantKey]
                    merchants[t.merchantKey] = MerchantSpend(
                        name = m?.name ?: MerchantNormalizer.displayName(t.merchantRaw),
                        amount = (m?.amount ?: 0L) + ledger.amount(t),
                        count = (m?.count ?: 0) + 1,
                    )
                }
            }

            val prev = HashMap<Long, Long>()
            for (t in prevTxns) {
                val cat = t.categoryId ?: continue
                prev[cat] = (prev[cat] ?: 0L) + ledger.signed(t)
            }

            val groupLines = groups.sortedBy { it.sortOrder }.map { g ->
                val lines = categories.filter { it.groupId == g.id }.sortedBy { it.sortOrder }.mapNotNull { c ->
                    val p = projected[c.id] ?: 0L
                    val a = actual[c.id] ?: 0L
                    if (c.archived && p == 0L && a == 0L) null else CategoryLine(c, p, a, counts[c.id] ?: 0)
                }
                GroupLine(g, lines)
            }

            return MonthReport(
                month = month,
                groups = groupLines,
                uncategorizedDebit = uncatDebit,
                uncategorizedCredit = uncatCredit,
                uncategorizedCount = uncatCount,
                dailySpend = ledger.daily(txns, month.lengthOfMonth()),
                prevDailySpend = ledger.daily(prevTxns, month.minusMonths(1).lengthOfMonth()),
                topMerchants = merchants.values.sortedByDescending { it.amount }.take(5),
                prevActualByCategory = prev,
                paidBack = paidBack,
            )
        }
    }
}
