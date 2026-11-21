package com.pawan.hisaab.data

import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId

class SharedBillsTest {
    private val groups = listOf(CategoryGroup(1, "DAILY LIVING", GroupKind.EXPENSE), CategoryGroup(2, "INCOME", GroupKind.INCOME))
    private val categories = listOf(Category(10, 1, "Dining Out"), Category(20, 2, "Misc"))
    private val nov = YearMonth.of(2026, 11)

    private fun at(month: YearMonth, day: Int) = month.atDay(day).atTime(20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun txn(id: Long, rupees: Int, dir: Direction, ts: Long, who: String, cat: Long? = null, share: Long? = null, paybackFor: Long? = null) =
        Txn(
            id = id, amountPaise = rupees * 100L, direction = dir, timestamp = ts, merchantRaw = who, merchantKey = who.lowercase(),
            categoryId = cat, source = TxnSource.SMS, sharePaise = share, paybackForId = paybackFor,
        )

    private fun report(txns: List<Txn>, month: YearMonth = nov, prev: List<Txn> = emptyList(), all: List<Txn> = txns + prev) =
        MonthReport.build(month, groups, categories, emptyList(), txns, prev, paybacks = all.filter { it.paybackForId != null })

    @Test fun friendsPayingBackLowerTheBillAndAreNotIncome() {
        val bill = txn(1, 2400, Direction.DEBIT, at(nov, 12), "Barbeque Nation", cat = 10)
        val backs = (2L..4L).map { txn(it, 600, Direction.CREDIT, at(nov, 13), "Friend $it", paybackFor = 1) }
        val r = report(listOf(bill) + backs)
        assertEquals(60000L, r.expense)
        assertEquals(0L, r.income)
        assertEquals(0, r.uncategorizedCount)
        assertEquals(180000L, r.paidBack)
        assertEquals(60000L, r.dailySpend[11])
    }

    @Test fun withoutLinkingTheyLookLikeIncome() {
        // The problem being fixed: same money, nothing linked.
        val bill = txn(1, 2400, Direction.DEBIT, at(nov, 12), "Barbeque Nation", cat = 10)
        val backs = (2L..4L).map { txn(it, 600, Direction.CREDIT, at(nov, 13), "Friend $it") }
        val r = report(listOf(bill) + backs)
        assertEquals(240000L, r.expense)
        assertEquals(180000L, r.income)
    }

    @Test fun aSharedBillCountsYourShareWhileFriendsStillOwe() {
        val bill = txn(1, 2400, Direction.DEBIT, at(nov, 12), "Barbeque Nation", cat = 10, share = Sharing.equalShare(240000, 4))
        val one = txn(2, 600, Direction.CREDIT, at(nov, 13), "Ravi", paybackFor = 1)
        val r = report(listOf(bill, one))
        assertEquals(60000L, r.expense)
        val b = Bill(bill, listOf(one))
        assertEquals(120000L, b.outstanding)
        assertEquals(60000L, b.cost)
    }

    @Test fun overpayingBringsTheCostDownButNeverBelowZero() {
        val bill = txn(1, 1000, Direction.DEBIT, at(nov, 2), "Cafe", cat = 10, share = 50000)
        assertEquals(30000L, Sharing.cost(bill, 70000))
        assertEquals(0L, Sharing.cost(bill, 150000))
        assertEquals(0L, Sharing.outstanding(bill, 70000))
    }

    @Test fun aPaybackNextMonthIsNotIncomeThereAndLowersTheOriginalMonth() {
        val oct = nov.minusMonths(1)
        val bill = txn(1, 2400, Direction.DEBIT, at(oct, 30), "Toit", cat = 10)
        val back = txn(2, 1200, Direction.CREDIT, at(nov, 2), "Asha", paybackFor = 1)
        val all = listOf(bill, back)
        assertEquals(0L, report(listOf(back), nov, prev = listOf(bill), all = all).income)
        assertEquals(120000L, report(listOf(bill), oct, all = all).expense)
    }

    @Test fun equalSplitRoundsYourShareUp() {
        assertEquals(33334L, Sharing.equalShare(100000, 3))
        val bill = txn(1, 1000, Direction.DEBIT, 0, "x", share = 33334)
        assertEquals(3, Sharing.peopleFor(bill))
        assertEquals(1, Sharing.peopleFor(bill.copy(sharePaise = null)))
        assertEquals(null, Sharing.peopleFor(bill.copy(sharePaise = 12345)))
    }

    @Test fun historyUsesTheSameRules() {
        val oct = nov.minusMonths(1)
        val bill = txn(1, 2400, Direction.DEBIT, at(nov, 12), "Barbeque Nation", cat = 10)
        val back = txn(2, 1800, Direction.CREDIT, at(nov, 13), "Friends", paybackFor = 1)
        val ledger = Ledger(groups, categories, Ledger.receivedByBill(listOf(back)))
        val h = History.build(listOf(oct, nov), listOf(bill, back), ledger)
        assertEquals(listOf(0L, 60000L), h.map { it.total })
        assertEquals(60000L, h[1].byCategory[10])
    }
}
