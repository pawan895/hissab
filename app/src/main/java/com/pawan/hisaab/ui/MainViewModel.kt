package com.pawan.hisaab.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pawan.hisaab.container
import com.pawan.hisaab.data.Bill
import com.pawan.hisaab.data.History
import com.pawan.hisaab.data.Ledger
import com.pawan.hisaab.data.MonthReport
import com.pawan.hisaab.data.MonthSpend
import com.pawan.hisaab.data.SheetIo
import com.pawan.hisaab.data.SheetItem
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.CategoryGroup
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.GroupKind
import com.pawan.hisaab.data.db.MerchantSuggestion
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.db.TxnSource
import com.pawan.hisaab.data.label
import com.pawan.hisaab.data.range
import com.pawan.hisaab.popup.OverlayController
import com.pawan.hisaab.popup.PopupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

private data class ReportInputs(
    val groups: List<CategoryGroup>,
    val categories: List<Category>,
    val budgets: List<com.pawan.hisaab.data.db.MonthlyBudget>,
    val txns: List<Txn>,
    val prev: List<Txn>,
)

data class PendingImport(val fileName: String, val items: List<SheetItem>, val month: YearMonth)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val repo = c.repo
    val settings = c.settings
    val lock = c.lock

    val month = MutableStateFlow(YearMonth.now())

    val groups: StateFlow<List<CategoryGroup>> = repo.groups.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val uncategorizedCount: StateFlow<Int> = repo.uncategorizedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val txns: StateFlow<List<Txn>> = month.flatMapLatest { repo.txnsForMonth(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Every credit that pays back a bill, across all months. */
    val paybacks: StateFlow<List<Txn>> = repo.paybacks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Shared bills and bills someone paid back, newest first. */
    val bills: StateFlow<List<Bill>> = combine(repo.bills, repo.paybacks) { bills, paybacks ->
        val byBill = paybacks.groupBy { it.paybackForId }
        bills.map { Bill(it, byBill[it.id].orEmpty()) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val report: StateFlow<MonthReport?> = month.flatMapLatest { m ->
        val base = combine(
            repo.groups, repo.categories, repo.budgetsForMonth(m), repo.txnsForMonth(m), repo.txnsForMonth(m.minusMonths(1)),
        ) { g, cats, b, t, p -> ReportInputs(g, cats, b, t, p) }
        combine(base, repo.paybacks) { i, paybacks -> MonthReport.build(m, i.groups, i.categories, i.budgets, i.txns, i.prev, paybacks) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Expense for the selected month and the five before it, oldest first. */
    val history: StateFlow<List<MonthSpend>> = month.flatMapLatest { m ->
        val months = (5 downTo 0).map { m.minusMonths(it.toLong()) }
        val from = months.first().range().first
        val to = m.range().second
        combine(repo.groups, repo.categories, repo.txnsBetween(from, to), repo.paybacks) { g, cats, t, p ->
            History.build(months, t, Ledger(g, cats, Ledger.receivedByBill(p)))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** One-shot messages for the snackbar. */
    val message = MutableStateFlow<String?>(null)

    /** Set when a notification asks to open a transaction. */
    val openTxnId = MutableStateFlow<Long?>(null)

    /** Set by the "Add expense" launcher shortcut. */
    val openAdd = MutableStateFlow(false)

    val merchants: StateFlow<List<MerchantSuggestion>> =
        repo.merchantSuggestions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pendingImport = MutableStateFlow<PendingImport?>(null)
    val busy = MutableStateFlow(false)

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { if (month.value < YearMonth.now()) month.value = month.value.plusMonths(1) }

    fun say(text: String) { message.value = text }

    /** Catch SMS that arrived while the receiver could not run. */
    fun onAppResumed() = viewModelScope.launch {
        val n = c.backfill.scanSinceLast()
        if (n > 0) say("Picked up $n transaction${if (n == 1) "" else "s"} from your SMS inbox")
    }

    fun scanInbox(months: Long) = viewModelScope.launch {
        if (!c.backfill.hasPermission()) { say("Allow SMS access first"); return@launch }
        busy.value = true
        val n = c.backfill.scanMonths(months)
        busy.value = false
        say(if (n == 0) "No new bank transactions found" else "Imported $n transactions from the last $months month(s)")
    }

    // ------------- transactions -------------

    suspend fun txn(id: Long) = repo.txn(id)

    fun save(txn: Txn) = viewModelScope.launch { repo.updateTxn(txn) }

    /** [method] is how it was paid ("UPI", "Card", "Cash", …); cash entries never wait for an SMS. */
    fun addManual(amountPaise: Long, direction: Direction, merchant: String, categoryId: Long?, note: String, timestamp: Long, method: String) =
        viewModelScope.launch {
            val source = if (method == "Cash") TxnSource.CASH else TxnSource.MANUAL
            repo.addManual(amountPaise, direction, merchant, categoryId, note, timestamp, source, method)
            say("Added")
        }

    fun categorizeAll(txns: List<Txn>, category: Category) = viewModelScope.launch {
        repo.categorizeMany(txns.map { it.id }, category.id)
        say("Filed ${txns.size} payments under ${category.name}")
    }

    /** Most-used categories for the quick row in the add sheet. */
    suspend fun frequentCategories(direction: Direction): List<Category> = repo.suggestions(
        Txn(amountPaise = 0, direction = direction, timestamp = 0, merchantRaw = "", merchantKey = "", source = TxnSource.MANUAL),
        limit = 6,
    )

    fun split(txn: Txn, splitPaise: Long, categoryId: Long?, note: String) = viewModelScope.launch {
        repo.split(txn, splitPaise, categoryId, note)
        say("Split ${com.pawan.hisaab.util.Money.format(splitPaise)} into a new entry")
    }

    /** Paybacks unlinked by a delete, so Undo can put them back on the bill. */
    private val unlinkedOnDelete = HashMap<Long, List<Txn>>()

    fun delete(txn: Txn) = viewModelScope.launch { unlinkedOnDelete[txn.id] = repo.deleteTxn(txn) }
    fun restore(txn: Txn) = viewModelScope.launch { repo.restoreTxn(txn, unlinkedOnDelete.remove(txn.id).orEmpty()) }

    // ------------- shared bills -------------

    fun linkPayback(credit: Txn, bill: Txn) = viewModelScope.launch {
        repo.linkPayback(credit.id, bill.id)
        say("Counted against ${com.pawan.hisaab.sms.MerchantNormalizer.displayName(bill.merchantRaw)}")
    }

    fun unlinkPayback(credit: Txn) = viewModelScope.launch { repo.unlinkPayback(credit.id) }

    fun setShare(bill: Txn, sharePaise: Long?) = viewModelScope.launch { repo.setShare(bill.id, sharePaise) }

    /** Stop waiting for the rest: what hasn't come back becomes your cost. */
    fun settle(bill: Bill) = viewModelScope.launch {
        repo.setShare(bill.txn.id, bill.txn.amountPaise - bill.received)
        say("Settled")
    }

    suspend fun paybackCandidates(credit: Txn, wide: Boolean): List<Bill> =
        repo.paybackCandidates(credit, limit = if (wide) 40 else 3, wide = wide)

    // ------------- budgets & categories -------------

    fun setBudget(categoryId: Long, paise: Long) = viewModelScope.launch { repo.setBudget(month.value, categoryId, paise) }

    fun copyBudgetFromPrevious() = viewModelScope.launch {
        val n = repo.copyBudgetFromPrevious(month.value)
        say(if (n == 0) "No earlier budget to copy" else "Copied $n budget lines")
    }

    fun addCategory(groupId: Long, name: String, emoji: String) = viewModelScope.launch { repo.addCategory(groupId, name, emoji) }
    fun updateCategory(category: Category) = viewModelScope.launch { repo.updateCategory(category) }
    fun addGroup(name: String, kind: GroupKind) = viewModelScope.launch { repo.addGroup(name, kind) }
    fun updateGroup(group: CategoryGroup) = viewModelScope.launch { repo.updateGroup(group) }

    // ------------- popup -------------

    fun previewPopup() {
        if (!OverlayController.enqueue(getApplication(), PopupRequest(txnId = -1, autoCategorized = false, preview = true))) {
            say("Allow 'Display over other apps' first")
        }
    }

    // ------------- files: Google Sheet CSV, exports, backup -------------

    private val resolver get() = getApplication<Application>().contentResolver

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        resolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
    }

    private suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")!!.bufferedWriter().use { it.write(text) }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun runFileOp(failure: String, block: suspend () -> Unit) = viewModelScope.launch {
        busy.value = true
        try {
            block()
        } catch (e: Exception) {
            say("$failure: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            busy.value = false
        }
    }

    fun pickedSheet(uri: Uri) = runFileOp("Couldn't read that file") {
        val name = displayName(uri) ?: "sheet.csv"
        val items = SheetIo.parse(readText(uri))
        if (items.isEmpty()) say("No budget rows found. Export the sheet tab as CSV (File → Download → .csv).")
        else pendingImport.value = PendingImport(name, items, SheetIo.guessMonth(name))
    }

    fun confirmImport(month: YearMonth, importActuals: Boolean) = runFileOp("Import failed") {
        val p = pendingImport.value ?: return@runFileOp
        pendingImport.value = null
        val r = c.transfer.importSheet(p.items, month, importActuals)
        this.month.value = month
        say("${month.label()}: ${r.budgets} budget lines, ${r.actuals} actuals, ${r.newCategories} new categories")
    }

    fun exportMonth(uri: Uri) = runFileOp("Export failed") {
        val r = report.value ?: return@runFileOp
        writeText(uri, com.pawan.hisaab.data.SheetIo.export(r))
        say("Saved ${r.month.label()} in your sheet's layout")
    }

    fun exportTransactions(uri: Uri) = runFileOp("Export failed") {
        val all = withContext(Dispatchers.IO) { c.db.txnDao().all() }
        writeText(uri, c.transfer.transactionsCsv(all, categories.value, groups.value))
        say("Exported ${all.size} transactions")
    }

    fun backup(uri: Uri) = runFileOp("Backup failed") {
        writeText(uri, c.transfer.backupJson())
        say("Backup saved")
    }

    fun restoreBackup(uri: Uri) = runFileOp("Restore failed") {
        val text = readText(uri)
        val n = withContext(Dispatchers.IO) { c.transfer.restoreJson(text) }
        say("Restored $n transactions")
    }
}
