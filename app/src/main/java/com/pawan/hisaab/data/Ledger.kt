package com.pawan.hisaab.data

import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import java.time.YearMonth

/**
 * Shared bills. You pay ₹2,400 for dinner, three friends send ₹600 each: the three
 * credits are paybacks linked to the bill, so the dinner cost you ₹600 and none of it
 * is income.
 */
object Sharing {
    /**
     * What a debit really cost you. Without a share set, it's the amount minus whatever
     * came back. With a share, it's your share until friends have paid back more than
     * theirs.
     */
    fun cost(bill: Txn, received: Long): Long {
        if (bill.direction != Direction.DEBIT) return bill.amountPaise
        val left = (bill.amountPaise - received).coerceAtLeast(0)
        return bill.sharePaise?.let { minOf(it, left) } ?: left
    }

    /** Still to come back from friends. Zero unless the bill has a share set. */
    fun outstanding(bill: Txn, received: Long): Long {
        val share = bill.sharePaise ?: return 0
        return (bill.amountPaise - share - received).coerceAtLeast(0)
    }

    /** Your share when a bill is split equally between [people], including you. */
    fun equalShare(amountPaise: Long, people: Int): Long = (amountPaise + people - 1) / people.coerceAtLeast(1)

    /** The number of people an equal split implies, or null for a custom share. */
    fun peopleFor(bill: Txn): Int? {
        val share = bill.sharePaise ?: return 1
        return (2..20).firstOrNull { equalShare(bill.amountPaise, it) == share }
    }
}

/** A shared bill with what's come back so far. */
data class Bill(val txn: Txn, val paybacks: List<Txn>) {
    val received: Long get() = paybacks.sumOf { it.amountPaise }
    val cost: Long get() = Sharing.cost(txn, received)
    val outstanding: Long get() = Sharing.outstanding(txn, received)
}

/**
 * The money rules every report and chart uses, so the home screen, the budget and the
 * category pages always agree.
 *
 * Sign rules: in income categories credits add and debits subtract; in expense and
 * savings categories debits add and credits subtract (a refund lowers Shopping).
 * Paybacks never count on their own: they lower their bill's cost instead.
 */
class Ledger(
    groups: List<CategoryGroup>,
    categories: List<Category>,
    /** Bill id → total paid back. */
    private val received: Map<Long, Long>,
) {
    private val kindByGroup = groups.associate { it.id to it.kind }
    val kindByCategory: Map<Long, GroupKind?> = categories.associate { it.id to kindByGroup[it.groupId] }

    fun kindOf(t: Txn): GroupKind? = t.categoryId?.let { kindByCategory[it] }

    fun isPayback(t: Txn) = t.paybackForId != null

    fun receivedFor(t: Txn): Long = received[t.id] ?: 0L

    /** Debits after paybacks; credits as they are. */
    fun amount(t: Txn): Long = if (t.direction == Direction.DEBIT) Sharing.cost(t, receivedFor(t)) else t.amountPaise

    /** Contribution to its category's actual (see sign rules). Zero for paybacks and uncategorized. */
    fun signed(t: Txn): Long {
        if (isPayback(t)) return 0
        val kind = kindOf(t) ?: return 0
        val positive = (kind == GroupKind.INCOME) == (t.direction == Direction.CREDIT)
        return if (positive) amount(t) else -amount(t)
    }

    /** Everyday spending: expense or not-yet-sorted debits, not sheet imports. */
    fun isEverydaySpend(t: Txn): Boolean =
        t.direction == Direction.DEBIT && t.source != TxnSource.IMPORT && !isPayback(t) &&
            (t.categoryId == null || kindOf(t) == GroupKind.EXPENSE || kindOf(t) == null)

    /** Expense actual by category plus uncategorized debits, for one set of transactions. */
    fun expenseByCategory(txns: List<Txn>): Pair<Map<Long, Long>, Long> {
        val byCat = HashMap<Long, Long>()
        var uncategorized = 0L
        for (t in txns) {
            if (isPayback(t)) continue
            val cat = t.categoryId
            val kind = kindOf(t)
            if (cat == null || kind == null) {
                if (t.direction == Direction.DEBIT) uncategorized += amount(t)
            } else if (kind == GroupKind.EXPENSE) {
                byCat[cat] = (byCat[cat] ?: 0L) + signed(t)
            }
        }
        return byCat to uncategorized
    }

    /** Everyday spending per day of [days] length, index 0 = day 1. */
    fun daily(txns: List<Txn>, days: Int): List<Long> {
        val out = LongArray(days)
        for (t in txns) {
            if (!isEverydaySpend(t)) continue
            val d = t.timestamp.dayOfMonth() - 1
            if (d in out.indices) out[d] += amount(t)
        }
        return out.toList()
    }

    companion object {
        fun receivedByBill(paybacks: List<Txn>): Map<Long, Long> =
            paybacks.filter { it.paybackForId != null }.groupBy { it.paybackForId!! }.mapValues { (_, v) -> v.sumOf { it.amountPaise } }
    }
}

/** Expense for one month, by category, using the same rules as [MonthReport.expense]. */
data class MonthSpend(val month: YearMonth, val byCategory: Map<Long, Long>, val uncategorized: Long) {
    val total: Long get() = byCategory.values.sum() + uncategorized
}

object History {
    /** One entry per month in [months], oldest first, even for months with nothing spent. */
    fun build(months: List<YearMonth>, txns: List<Txn>, ledger: Ledger): List<MonthSpend> {
        val byMonth = txns.groupBy { it.timestamp.toYearMonth() }
        return months.sorted().map { m ->
            val (byCat, uncat) = ledger.expenseByCategory(byMonth[m].orEmpty())
            MonthSpend(m, byCat, uncat)
        }
    }
}
