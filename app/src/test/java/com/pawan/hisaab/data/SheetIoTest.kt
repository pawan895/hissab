package com.pawan.hisaab.data

import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.MonthlyBudget
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.YearMonth

class SheetIoTest {
    private val csv: String by lazy {
        // Gradle runs unit tests from the module directory; the user's sheet lives in the repo root.
        // The sheet is personal data and is git-ignored, so these tests are skipped when it is absent.
        val file = listOf(File("../Monthly Budget - October.csv"), File("Monthly Budget - October.csv"))
            .firstOrNull { it.exists() }
        assumeTrue("Monthly Budget - October.csv not found; skipping sheet tests", file != null)
        file!!.readText()
    }

    @Test fun parsesOctoberSheet() {
        val items = SheetIo.parse(csv)
        val groups = items.map { it.groupName }.distinct()
        assertEquals(listOf("Income", "Savings", "Home", "Transportation", "Daily Living", "Entertainment", "Health", "Home & Family"), groups)
        val salary = items.first { it.category == "Salary / Wages" }
        assertEquals(8_470_000L, salary.projected)
        assertEquals(8_470_000L, salary.actual)
        val dad = items.first { it.category == "Dad Phone" }
        assertEquals("Home & Family", dad.groupName)
        assertEquals(3_250_000L, dad.actual)
        assertTrue(items.none { it.category.equals("TOTAL", true) })
    }

    @Test fun reportMatchesSheetTotals() {
        val items = SheetIo.parse(csv)
        val month = YearMonth.of(2026, 10)
        val groups = items.map { it.groupName to it.kind }.distinct().mapIndexed { i, (n, k) -> CategoryGroup(i + 1L, n, k, i) }
        val categories = items.mapIndexed { i, it ->
            Category(i + 1L, groups.first { g -> g.name == it.groupName }.id, it.category, sortOrder = i)
        }
        val budgets = items.mapIndexedNotNull { i, it -> it.projected?.let { p -> MonthlyBudget(month.toString(), i + 1L, p) } }
        val noon = month.atDay(1).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val txns = items.mapIndexedNotNull { i, it ->
            val a = it.actual ?: return@mapIndexedNotNull null
            if (a == 0L) return@mapIndexedNotNull null
            Txn(
                amountPaise = a,
                direction = if (it.kind == GroupKind.INCOME) Direction.CREDIT else Direction.DEBIT,
                timestamp = noon, merchantRaw = "x", merchantKey = "x", categoryId = i + 1L, source = TxnSource.IMPORT,
            )
        }

        val r = MonthReport.build(month, groups, categories, budgets, txns, emptyList())
        assertEquals(9_420_000L, r.income)
        assertEquals(6_676_200L, r.expense)
        assertEquals(2_950_000L, r.savings)
        assertEquals(-206_200L, r.net)
        assertEquals(10_220_000L, r.projectedIncome)
        assertEquals(2_245_000L, r.projectedExpense)
        assertEquals(4_705_700L, r.groupsOf(GroupKind.EXPENSE).first { it.group.name == "Home & Family" }.actual)

        // Export round-trips through the parser.
        val again = SheetIo.parse(SheetIo.export(r))
        assertEquals(items.size, again.size)
        assertEquals(items.sumOf { it.actual ?: 0L }, again.sumOf { it.actual ?: 0L })
    }

    @Test fun guessesMonthFromFileName() {
        assertEquals(YearMonth.of(2026, 10), SheetIo.guessMonth("Monthly Budget - October.csv", YearMonth.of(2026, 11)))
        assertEquals(YearMonth.of(2025, 12), SheetIo.guessMonth("Monthly Budget - December.csv", YearMonth.of(2026, 11)))
    }
}
