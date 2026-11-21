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
import com.pawan.hisaab.data.seed.DefaultCategories
import com.pawan.hisaab.data.seed.KnownMerchants
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.sms.SmsParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.time.YearMonth

data class IngestResult(val txn: Txn, val autoCategorized: Boolean)

class FinanceRepository(
    private val db: AppDatabase,
    private val settings: AppSettings,
) {
    private val categoryDao = db.categoryDao()
    private val txnDao = db.txnDao()
    private val budgetDao = db.budgetDao()
    private val ruleDao = db.merchantRuleDao()
    private val seedLock = Mutex()
    private val ingestLock = Mutex()

    val groups = categoryDao.groupsFlow()
    val categories = categoryDao.categoriesFlow()
    val uncategorizedCount = txnDao.uncategorizedCount()

    fun txnsForMonth(month: YearMonth) = month.range().let { (from, to) -> txnDao.between(from, to) }
    fun txnsBetween(from: Long, to: Long) = txnDao.between(from, to)
    fun budgetsForMonth(month: YearMonth) = budgetDao.forMonthFlow(month.toString())

    suspend fun ensureSeeded() = seedLock.withLock {
        if (categoryDao.countGroups() > 0) return@withLock
        db.withTransaction {
            DefaultCategories.groups.forEachIndexed { gi, g ->
                val groupId = categoryDao.insertGroup(CategoryGroup(name = g.name, kind = g.kind, sortOrder = gi))
                g.categories.forEachIndexed { ci, (name, emoji) ->
                    categoryDao.insertCategory(Category(groupId = groupId, name = name, emoji = emoji, sortOrder = ci))
                }
            }
        }
    }

    // ---------------- SMS ingest ----------------

    /** Parses an SMS and stores it. Returns null for non-transaction or duplicate messages. */
    suspend fun ingestSms(sender: String, body: String, timestamp: Long): IngestResult? = ingestLock.withLock {
        val parsed = SmsParser.parse(sender, body) ?: return@withLock null
        if (parsed.refNo.isNotEmpty() && txnDao.countByRef(parsed.refNo, parsed.amountPaise) > 0) return@withLock null

        val smsHash = hash(SmsParser.normalize(body))
        if (txnDao.countByHash(smsHash) > 0) return@withLock null

        // Already added by hand because the SMS was late: attach the SMS to that entry instead of duplicating it.
        txnDao.findManualMatch(
            parsed.amountPaise, parsed.direction, timestamp - MANUAL_MATCH_WINDOW_MS, timestamp + MANUAL_MATCH_WINDOW_MS, timestamp,
        )?.let { manual ->
            val merged = manual.copy(
                source = TxnSource.SMS,
                smsBody = body,
                smsHash = smsHash,
                bank = parsed.bank,
                accountLast4 = parsed.accountLast4,
                refNo = parsed.refNo,
                balancePaise = parsed.balancePaise,
            )
            txnDao.update(merged)
            return@withLock if (merged.categoryId == null) IngestResult(merged, false) else null
        }

        val key = MerchantNormalizer.key(parsed.payee)
        // Someone who has paid you back before is probably doing it again: ask rather than file it as income.
        val repays = parsed.direction == Direction.CREDIT && txnDao.paybackCountFrom(key) > 0
        val known = if (settings.autoCategorize.value && !repays) knownCategoryId(key, parsed.payee, parsed.direction) else null

        val txn = Txn(
            amountPaise = parsed.amountPaise,
            direction = parsed.direction,
            timestamp = timestamp,
            merchantRaw = parsed.payee,
            merchantKey = key,
            categoryId = known,
            bank = parsed.bank,
            accountLast4 = parsed.accountLast4,
            refNo = parsed.refNo,
            source = TxnSource.SMS,
            smsBody = body,
            smsHash = smsHash,
            balancePaise = parsed.balancePaise,
            needsReview = !parsed.confident,
        )
        val id = txnDao.insert(txn)
        if (id == -1L) null else IngestResult(txn.copy(id = id), known != null)
    }

    /**
     * Category to file a payee under without asking: what you picked for it before, or
     * else the default for a well-known merchant (Zomato, Zepto, Uber, …).
     */
    suspend fun knownCategoryId(merchantKey: String, merchantRaw: String, direction: Direction): Long? {
        if (merchantKey == "unknown") return null
        val active = categoryDao.allCategories().filter { !it.archived }
        ruleDao.get(merchantKey, direction)
            ?.takeIf { it.hitCount >= AppSettings.AUTO_CATEGORIZE_AFTER }
            ?.let { rule -> active.firstOrNull { it.id == rule.categoryId }?.let { return it.id } }
        if (direction != Direction.DEBIT) return null
        val name = KnownMerchants.categoryFor(merchantKey, merchantRaw) ?: return null
        return active.firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
    }

    /** Files every unsorted payment whose merchant is known. Returns how many were filed. */
    suspend fun fileKnownMerchants(): Int {
        if (!settings.autoCategorize.value) return 0
        var filed = 0
        for (t in txnDao.uncategorized()) {
            val cat = knownCategoryId(t.merchantKey, t.merchantRaw, t.direction) ?: continue
            txnDao.update(t.copy(categoryId = cat))
            filed++
        }
        return filed
    }

    // ---------------- categorizing & learning ----------------

    /** Also files other unsorted payments to the same merchant, unless [applyToMerchant] is false. */
    suspend fun categorize(txnId: Long, categoryId: Long, note: String? = null, applyToMerchant: Boolean = true) {
        val txn = txnDao.byId(txnId) ?: return
        txnDao.update(txn.copy(categoryId = categoryId, note = note ?: txn.note, paybackForId = null))
        learn(txn.merchantKey, txn.direction, categoryId)
        if (applyToMerchant) fileSameMerchant(txn.merchantKey, txn.direction, categoryId)
    }

    private suspend fun fileSameMerchant(merchantKey: String, direction: Direction, categoryId: Long) {
        if (merchantKey == "unknown") return
        txnDao.uncategorizedForMerchant(merchantKey, direction).forEach { txnDao.update(it.copy(categoryId = categoryId)) }
    }

    private suspend fun learn(merchantKey: String, direction: Direction, categoryId: Long) {
        if (merchantKey == "unknown") return
        val existing = ruleDao.get(merchantKey, direction)
        val hits = if (existing?.categoryId == categoryId) existing.hitCount + 1 else 1
        ruleDao.upsert(MerchantRule(merchantKey, direction, categoryId, hits))
    }

    /** Best guesses first: learned rule, then most-used categories, then sensible defaults. */
    suspend fun suggestions(txn: Txn, limit: Int = 8): List<Category> {
        val cats = categoryDao.allCategories().filter { !it.archived }
        val byId = cats.associateBy { it.id }
        val groupKinds = categoryDao.allGroups().associate { it.id to it.kind }
        val wantedKinds = if (txn.direction == Direction.DEBIT) {
            setOf(GroupKind.EXPENSE, GroupKind.SAVINGS)
        } else {
            setOf(GroupKind.INCOME)
        }
        val out = LinkedHashSet<Category>()
        ruleDao.get(txn.merchantKey, txn.direction)?.let { r -> byId[r.categoryId]?.let(out::add) }
        if (txn.direction == Direction.DEBIT) {
            KnownMerchants.categoryFor(txn.merchantKey, txn.merchantRaw)?.let { name ->
                cats.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let(out::add)
            }
        }
        txnDao.topCategories(txn.direction, limit * 2).forEach { cc ->
            byId[cc.categoryId]?.takeIf { groupKinds[it.groupId] in wantedKinds }?.let(out::add)
        }
        val defaults = if (txn.direction == Direction.DEBIT) {
            DefaultCategories.defaultDebitSuggestions
        } else {
            DefaultCategories.defaultCreditSuggestions
        }
        defaults.forEach { name ->
            cats.firstOrNull { it.name == name && groupKinds[it.groupId] in wantedKinds }?.let(out::add)
        }
        cats.filter { groupKinds[it.groupId] in wantedKinds }.forEach { if (out.size < limit) out.add(it) }
        return out.take(limit)
    }

    /** The category this merchant is known for, to highlight in the popup. */
    suspend fun learnedCategoryFor(txn: Txn): Category? =
        knownCategoryId(txn.merchantKey, txn.merchantRaw, txn.direction)?.let { categoryDao.category(it) }

    val merchantSuggestions = txnDao.merchantSuggestions()

    /** Files several payments at once (e.g. every Zomato order this month) and learns the choice. */
    suspend fun categorizeMany(txnIds: List<Long>, categoryId: Long) {
        var first: Txn? = null
        db.withTransaction {
            txnIds.forEach { id ->
                val t = txnDao.byId(id) ?: return@forEach
                if (first == null) first = t
                txnDao.update(t.copy(categoryId = categoryId))
            }
        }
        first?.let { learn(it.merchantKey, it.direction, categoryId) }
    }

    // ---------------- CRUD ----------------

    suspend fun txn(id: Long) = txnDao.byId(id)
    suspend fun category(id: Long) = categoryDao.category(id)
    suspend fun allCategories() = categoryDao.allCategories()
    suspend fun allGroups() = categoryDao.allGroups()

    suspend fun addManual(
        amountPaise: Long,
        direction: Direction,
        merchant: String,
        categoryId: Long?,
        note: String,
        timestamp: Long,
        source: TxnSource = TxnSource.MANUAL,
        method: String = "",
    ): Long {
        val key = MerchantNormalizer.key(merchant)
        val category = categoryId ?: knownCategoryId(key, merchant, direction)
        val id = txnDao.insert(
            Txn(
                amountPaise = amountPaise, direction = direction, timestamp = timestamp,
                merchantRaw = merchant, merchantKey = key, categoryId = category,
                note = note, source = source, bank = method,
            ),
        )
        if (categoryId != null) learn(key, direction, categoryId)
        return id
    }

    suspend fun updateTxn(txn: Txn) {
        val old = txnDao.byId(txn.id)
        val updated = txn.copy(merchantKey = MerchantNormalizer.key(txn.merchantRaw), needsReview = false)
        txnDao.update(updated)
        if (updated.categoryId != null && updated.categoryId != old?.categoryId) {
            learn(updated.merchantKey, updated.direction, updated.categoryId)
            fileSameMerchant(updated.merchantKey, updated.direction, updated.categoryId)
        }
    }

    /** Moves [splitPaise] out of [txn] into a new transaction with its own category. */
    suspend fun split(txn: Txn, splitPaise: Long, categoryId: Long?, note: String) {
        require(splitPaise in 1 until txn.amountPaise)
        db.withTransaction {
            txnDao.update(txn.copy(amountPaise = txn.amountPaise - splitPaise))
            txnDao.insert(
                txn.copy(
                    id = 0, amountPaise = splitPaise, categoryId = categoryId, note = note,
                    smsHash = null, refNo = "", source = TxnSource.MANUAL, sharePaise = null, paybackForId = null,
                ),
            )
        }
    }

    /** Returns the paybacks that were linked to it, so an undo can link them again. */
    suspend fun deleteTxn(txn: Txn): List<Txn> = db.withTransaction {
        val paybacks = txnDao.paybacksFor(txn.id)
        txnDao.unlinkPaybacks(txn.id)
        txnDao.delete(txn)
        paybacks
    }

    /** Undo for a delete: puts the exact row back, with its paybacks. */
    suspend fun restoreTxn(txn: Txn, paybacks: List<Txn> = emptyList()) = db.withTransaction {
        txnDao.insert(txn)
        paybacks.forEach { p -> txnDao.byId(p.id)?.let { txnDao.update(it.copy(paybackForId = txn.id)) } }
    }

    // ---------------- shared bills ----------------

    val paybacks = txnDao.paybacks()
    val bills = txnDao.bills()

    /** Marks [creditId] as a friend paying back part of [billId]. */
    suspend fun linkPayback(creditId: Long, billId: Long) {
        val credit = txnDao.byId(creditId) ?: return
        if (credit.direction != Direction.CREDIT || creditId == billId) return
        txnDao.update(credit.copy(paybackForId = billId, categoryId = null))
    }

    suspend fun unlinkPayback(creditId: Long) {
        val credit = txnDao.byId(creditId) ?: return
        txnDao.update(credit.copy(paybackForId = null))
    }

    /** Your own share of a bill you paid for everyone; null stops treating it as shared. */
    suspend fun setShare(billId: Long, sharePaise: Long?) {
        val bill = txnDao.byId(billId) ?: return
        if (bill.direction != Direction.DEBIT) return
        txnDao.update(bill.copy(sharePaise = sharePaise?.coerceIn(0, bill.amountPaise)))
    }

    suspend fun bill(id: Long): Bill? = txnDao.byId(id)?.let { Bill(it, txnDao.paybacksFor(id)) }

    /**
     * Bills [credit] might be paying back, best guess first: shared bills still waiting
     * for money, then recent bigger payments (the dinner you paid in full). [wide] lists
     * every payment of the last two months, for picking by hand.
     */
    suspend fun paybackCandidates(credit: Txn, limit: Int = 3, wide: Boolean = false): List<Bill> {
        if (credit.direction != Direction.CREDIT) return emptyList()
        val byBill = txnDao.allPaybacks().filter { it.id != credit.id }.groupBy { it.paybackForId!! }
        val kinds = categoryDao.allGroups().associate { it.id to it.kind }
        val kindOf = categoryDao.allCategories().associate { it.id to kinds[it.groupId] }
        val debits = txnDao.listBetween(credit.timestamp - CANDIDATE_WINDOW_MS, credit.timestamp + HOUR_MS)
            .filter { t ->
                t.direction == Direction.DEBIT && t.source != TxnSource.IMPORT &&
                    (t.categoryId == null || kindOf[t.categoryId] == GroupKind.EXPENSE)
            }
            .map { Bill(it, byBill[it.id].orEmpty()) }

        val waiting = debits.filter { it.txn.sharePaise != null && it.outstanding > 0 }
            .sortedWith(compareByDescending<Bill> { it.outstanding >= credit.amountPaise }.thenByDescending { it.txn.timestamp })
        val recent = debits.filter { b ->
            b.txn.sharePaise == null && (
                wide || (
                    b.txn.timestamp >= credit.timestamp - RECENT_BILL_MS &&
                        b.txn.amountPaise - b.received > credit.amountPaise
                    )
                )
        }.sortedWith(compareByDescending<Bill> { it.paybacks.isNotEmpty() }.thenByDescending { it.txn.timestamp })
        val linked = credit.paybackForId?.let { id -> debits.firstOrNull { it.txn.id == id } }
        return (listOfNotNull(linked) + waiting + recent).distinctBy { it.txn.id }.take(limit)
    }

    suspend fun addCategory(groupId: Long, name: String, emoji: String): Long {
        val order = categoryDao.allCategories().filter { it.groupId == groupId }.maxOfOrNull { it.sortOrder + 1 } ?: 0
        return categoryDao.insertCategory(Category(groupId = groupId, name = name.trim(), emoji = emoji.ifBlank { "•" }, sortOrder = order))
    }

    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)

    suspend fun addGroup(name: String, kind: GroupKind): Long {
        val order = categoryDao.allGroups().maxOfOrNull { it.sortOrder + 1 } ?: 0
        return categoryDao.insertGroup(CategoryGroup(name = name.trim(), kind = kind, sortOrder = order))
    }

    suspend fun updateGroup(group: CategoryGroup) = categoryDao.updateGroup(group)

    // ---------------- budgets ----------------

    suspend fun setBudget(month: YearMonth, categoryId: Long, projectedPaise: Long) =
        budgetDao.upsert(MonthlyBudget(month.toString(), categoryId, projectedPaise))

    /** Copies the most recent earlier month's projections. Returns how many rows were copied. */
    suspend fun copyBudgetFromPrevious(month: YearMonth): Int {
        val prev = budgetDao.latestMonthBefore(month.toString()) ?: return 0
        val rows = budgetDao.forMonth(prev).map { it.copy(month = month.toString()) }
        budgetDao.upsertAll(rows)
        return rows.size
    }

    companion object {
        /** How far apart a hand-added payment and its late SMS can be and still be merged. */
        private const val MANUAL_MATCH_WINDOW_MS = 2L * 24 * 60 * 60 * 1000
        private const val HOUR_MS = 60L * 60 * 1000
        private const val DAY_MS = 24 * HOUR_MS
        /** How far back to look for a bill that a credit pays back. */
        private const val CANDIDATE_WINDOW_MS = 60 * DAY_MS
        /** Unshared payments are only offered when they're this recent. */
        private const val RECENT_BILL_MS = 10 * DAY_MS
    }

    private fun hash(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
