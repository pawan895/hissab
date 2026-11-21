package com.pawan.hisaab.data

import androidx.room.withTransaction
import com.pawan.hisaab.data.db.AppDatabase
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.MerchantRule
import com.pawan.hisaab.data.db.MonthlyBudget
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

@Serializable
data class Backup(
    val version: Int = 1,
    val groups: List<CategoryGroup>,
    val categories: List<Category>,
    val transactions: List<Txn>,
    val budgets: List<MonthlyBudget>,
    val rules: List<MerchantRule>,
)

data class ImportSummary(val budgets: Int, val actuals: Int, val newCategories: Int)

/** Everything that moves data in or out of the app. All of it goes through local files only. */
class DataTransfer(private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun importSheet(items: List<SheetItem>, month: YearMonth, importActuals: Boolean): ImportSummary = db.withTransaction {
        val categoryDao = db.categoryDao()
        val groups = categoryDao.allGroups().toMutableList()
        val categories = categoryDao.allCategories().toMutableList()
        var budgets = 0
        var actuals = 0
        var created = 0
        val noon = month.atDay(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        for (item in items) {
            val group = groups.firstOrNull { it.name.equals(item.groupName, true) }
                ?: groups.firstOrNull { item.kind != GroupKind.EXPENSE && it.kind == item.kind }
                ?: CategoryGroup(name = item.groupName, kind = item.kind, sortOrder = groups.size).let {
                    val g = it.copy(id = categoryDao.insertGroup(it)); groups += g; g
                }
            val inGroup = categories.filter { it.groupId == group.id }
            val category = inGroup.firstOrNull { sameName(it.name, item.category) }
                ?: Category(groupId = group.id, name = item.category, sortOrder = inGroup.size).let {
                    created++
                    val c = it.copy(id = categoryDao.insertCategory(it)); categories += c; c
                }

            item.projected?.takeIf { it != 0L }?.let {
                db.budgetDao().upsert(MonthlyBudget(month.toString(), category.id, it)); budgets++
            }
            val actual = item.actual ?: 0L
            if (importActuals && actual != 0L) {
                val positiveDir = if (item.kind == GroupKind.INCOME) Direction.CREDIT else Direction.DEBIT
                val negativeDir = if (positiveDir == Direction.CREDIT) Direction.DEBIT else Direction.CREDIT
                val id = db.txnDao().insert(
                    Txn(
                        amountPaise = kotlin.math.abs(actual),
                        direction = if (actual > 0) positiveDir else negativeDir,
                        timestamp = noon,
                        merchantRaw = "Sheet: ${item.category}",
                        merchantKey = "sheet import",
                        categoryId = category.id,
                        note = "Imported from Google Sheet",
                        source = TxnSource.IMPORT,
                        // Re-importing the same sheet replaces nothing and adds nothing.
                        smsHash = "import:$month:${category.id}",
                    ),
                )
                if (id != -1L) actuals++
            }
        }
        ImportSummary(budgets, actuals, created)
    }

    fun transactionsCsv(txns: List<Txn>, categories: List<Category>, groups: List<CategoryGroup>): String {
        val cat = categories.associateBy { it.id }
        val grp = groups.associateBy { it.id }
        val header = listOf("Date", "Time", "Type", "Amount", "Merchant", "Category", "Group", "Note", "Bank", "Account", "Ref", "Source")
        val rows = txns.sortedBy { it.timestamp }.map { t ->
            val c = t.categoryId?.let { cat[it] }
            listOf(
                t.timestamp.formatDate("yyyy-MM-dd"), t.timestamp.formatDate("HH:mm"),
                if (t.direction == Direction.DEBIT) "Debit" else "Credit",
                com.pawan.hisaab.util.Money.plain(t.amountPaise),
                t.merchantRaw, c?.name.orEmpty(), c?.let { grp[it.groupId]?.name }.orEmpty(),
                t.note, t.bank, t.accountLast4, t.refNo, t.source.name.lowercase(Locale.ROOT),
            )
        }
        return (listOf(header) + rows).joinToString("\n") { Csv.line(it) } + "\n"
    }

    suspend fun backupJson(): String = json.encodeToString(
        Backup.serializer(),
        Backup(
            groups = db.categoryDao().allGroups(),
            categories = db.categoryDao().allCategories(),
            transactions = db.txnDao().all(),
            budgets = db.budgetDao().all(),
            rules = db.merchantRuleDao().all(),
        ),
    )

    /** Replaces everything in the database with the backup. */
    suspend fun restoreJson(text: String): Int {
        val backup = json.decodeFromString(Backup.serializer(), text)
        db.clearAllTables()
        db.withTransaction {
            db.categoryDao().insertGroups(backup.groups)
            db.categoryDao().insertCategories(backup.categories)
            db.txnDao().insertAll(backup.transactions)
            db.budgetDao().upsertAll(backup.budgets)
            db.merchantRuleDao().upsertAll(backup.rules)
        }
        return backup.transactions.size
    }

    private fun sameName(a: String, b: String): Boolean {
        val x = a.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        val y = b.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
        if (x == y) return true
        // Tolerate small typos such as "Public Transporation".
        return x.length >= 6 && y.length >= 6 && levenshtein(x, y) <= 2
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
