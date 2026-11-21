package com.pawan.hisaab

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.data.label
import com.pawan.hisaab.popup.CategoryNotification
import com.pawan.hisaab.ui.MainViewModel
import com.pawan.hisaab.ui.budget.BudgetScreen
import com.pawan.hisaab.ui.components.Hairline
import com.pawan.hisaab.ui.components.Pill
import com.pawan.hisaab.ui.dashboard.DashboardScreen
import com.pawan.hisaab.ui.lock.LockScreen
import com.pawan.hisaab.ui.onboarding.OnboardingScreen
import com.pawan.hisaab.ui.settings.CSV_MIME_TYPES
import com.pawan.hisaab.ui.settings.CategoriesScreen
import com.pawan.hisaab.ui.settings.SettingsScreen
import com.pawan.hisaab.ui.theme.FinTheme
import com.pawan.hisaab.ui.theme.HisaabTheme
import com.pawan.hisaab.ui.transactions.TransactionsScreen
import com.pawan.hisaab.ui.transactions.TxnEditorSheet
import com.pawan.hisaab.ui.transactions.TxnFilter
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Keep balances out of the recent-apps preview.
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        handleIntent(intent)
        setContent { HisaabTheme { Root(vm) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ACTION_ADD) vm.openAdd.value = true
        val id = intent?.getLongExtra(CategoryNotification.EXTRA_TXN_ID, -1L) ?: -1L
        if (id > 0) {
            vm.openTxnId.value = id
            CategoryNotification.cancel(this, id)
        }
    }
}

/** Long-press the app icon → "Add expense". */
const val ACTION_ADD = "com.pawan.hisaab.ADD"

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Activity("Activity", Icons.AutoMirrored.Filled.List),
    Budget("Budget", Icons.Default.DateRange),
}

/** Full-screen pages that sit above the tabs. */
private enum class Page { None, Settings, Categories }

@Composable
private fun Root(vm: MainViewModel) {
    val onboarded by vm.settings.onboardingDone.collectAsState()
    val report by vm.report.collectAsState()
    val txns by vm.txns.collectAsState()
    val groups by vm.groups.collectAsState()
    val categories by vm.categories.collectAsState()
    val month by vm.month.collectAsState()
    val uncategorized by vm.uncategorizedCount.collectAsState()
    val message by vm.message.collectAsState()
    val openTxnId by vm.openTxnId.collectAsState()
    val pendingImport by vm.pendingImport.collectAsState()
    val busy by vm.busy.collectAsState()
    val lockEnabled by vm.lock.enabled.collectAsState()
    val locked by vm.lock.locked.collectAsState()
    val biometricOn by vm.lock.biometric.collectAsState()
    val activity = LocalContext.current as FragmentActivity

    var tab by remember { mutableStateOf(Tab.Home) }
    var page by remember { mutableStateOf(Page.None) }
    var filter by remember { mutableStateOf(TxnFilter.All) }
    var editing by remember { mutableStateOf<Txn?>(null) }
    var creating by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onAppResumed() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.lock.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.lock.onForeground() }
    LaunchedEffect(onboarded) { if (!onboarded) vm.lock.unlockForSetup() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }
    val openAdd by vm.openAdd.collectAsState()
    LaunchedEffect(openAdd) {
        if (openAdd) {
            creating = true
            editing = null
            page = Page.None
            vm.openAdd.value = false
        }
    }
    LaunchedEffect(openTxnId) {
        openTxnId?.let { id ->
            vm.txn(id)?.let { editing = it; tab = Tab.Activity; page = Page.None }
            vm.openTxnId.value = null
        }
    }

    val importSheet = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickedSheet) }
    val exportMonth = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let(vm::exportMonth) }

    val deleteWithUndo: (Txn) -> Unit = { t ->
        vm.delete(t)
        scope.launch {
            if (snackbar.showSnackbar("Deleted", actionLabel = "Undo") == SnackbarResult.ActionPerformed) vm.restore(t)
        }
    }

    BackHandler(enabled = page != Page.None) { page = if (page == Page.Categories) Page.Settings else Page.None }

    val snackbarHost: @Composable () -> Unit = {
        SnackbarHost(snackbar) { data ->
            Snackbar(
                data,
                containerColor = FinTheme.colors.ink,
                contentColor = MaterialTheme.colorScheme.background,
                actionColor = MaterialTheme.colorScheme.background,
                shape = MaterialTheme.shapes.small,
            )
        }
    }

    if (onboarded && lockEnabled && locked) {
        LockScreen(
            lock = vm.lock,
            onBiometric = if (biometricOn && vm.lock.biometricAvailable()) ({ vm.lock.promptBiometric(activity) }) else null,
        )
        return
    }

    if (!onboarded) {
        Scaffold(snackbarHost = snackbarHost) { pad ->
            Column(Modifier.padding(pad)) {
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = FinTheme.colors.ink)
                OnboardingScreen(vm, onImportSheet = { importSheet.launch(CSV_MIME_TYPES) })
            }
        }
    } else {
        Scaffold(
            topBar = {
                Header(
                    page = page,
                    month = month,
                    tab = tab,
                    busy = busy,
                    onBack = { page = if (page == Page.Categories) Page.Settings else Page.None },
                    onPrev = vm::previousMonth,
                    onNext = vm::nextMonth,
                    onSettings = { page = Page.Settings },
                )
            },
            bottomBar = {
                if (page == Page.None) BottomBar(tab, uncategorized) { tab = it }
            },
            floatingActionButton = {
                if (page == Page.None && tab != Tab.Budget) {
                    FloatingActionButton(
                        onClick = { creating = true },
                        shape = CircleShape,
                        containerColor = FinTheme.colors.ink,
                        contentColor = MaterialTheme.colorScheme.background,
                    ) { Icon(Icons.Default.Add, "Add a payment") }
                }
            },
            snackbarHost = snackbarHost,
        ) { pad ->
            val r = report
            when {
                page == Page.Categories -> CategoriesScreen(vm, groups, categories, pad)
                page == Page.Settings -> SettingsScreen(vm, onManageCategories = { page = Page.Categories }, contentPadding = pad)
                r == null -> Box(Modifier.padding(pad).fillMaxSize())
                tab == Tab.Home -> DashboardScreen(
                    report = r,
                    txns = txns,
                    categories = categories,
                    uncategorizedTotal = uncategorized,
                    onCategorizeClick = { filter = TxnFilter.Uncategorized; tab = Tab.Activity },
                    onSeeAll = { filter = TxnFilter.All; tab = Tab.Activity },
                    onBudget = { tab = Tab.Budget },
                    onOpen = { editing = it },
                    onScanInbox = { vm.scanInbox(3) },
                    contentPadding = pad,
                )
                tab == Tab.Activity -> TransactionsScreen(
                    txns = txns,
                    categories = categories,
                    categoryGroups = groups,
                    filter = filter,
                    onFilter = { filter = it },
                    onOpen = { editing = it },
                    onDelete = deleteWithUndo,
                    onFileAll = vm::categorizeAll,
                    contentPadding = pad,
                )
                else -> BudgetScreen(
                    report = r,
                    onSetBudget = vm::setBudget,
                    onCopyPrevious = vm::copyBudgetFromPrevious,
                    onExport = { exportMonth.launch("Monthly Budget - $month.csv") },
                    contentPadding = pad,
                )
            }
        }
    }

    if (editing != null || creating) {
        TxnEditorSheet(
            txn = editing,
            groups = groups,
            categories = categories,
            vm = vm,
            onDelete = deleteWithUndo,
            onClose = { editing = null; creating = false },
        )
    }

    pendingImport?.let { p ->
        var importMonth by remember(p) { mutableStateOf(p.month) }
        var actuals by remember(p) { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { vm.pendingImport.value = null },
            containerColor = MaterialTheme.colorScheme.background,
            title = { Text("Import sheet") },
            text = {
                Column {
                    Text("${p.items.size} lines found in ${p.fileName}. Which month is it?", color = FinTheme.colors.ink2)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        IconButton(onClick = { importMonth = importMonth.minusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Earlier")
                        }
                        Text(importMonth.label(), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { importMonth = importMonth.plusMonths(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Later")
                        }
                    }
                    Pill("Also import the Actual column", actuals, { actuals = !actuals })
                }
            },
            confirmButton = { TextButton(onClick = { vm.confirmImport(importMonth, actuals) }) { Text("Import") } },
            dismissButton = { TextButton(onClick = { vm.pendingImport.value = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Header(
    page: Page,
    month: YearMonth,
    tab: Tab,
    busy: Boolean,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = FinTheme.colors
    Column(Modifier.background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page != Page.None) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(if (page == Page.Settings) "Settings" else "Categories", style = MaterialTheme.typography.headlineSmall)
            } else {
                Spacer(Modifier.width(12.dp))
                Text(month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), style = MaterialTheme.typography.headlineMedium)
                if (month.year != YearMonth.now().year) {
                    Text(" ${month.year}", style = MaterialTheme.typography.headlineMedium, color = c.ink3)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = c.ink2)
                }
                val canNext = month < YearMonth.now()
                IconButton(onClick = onNext, enabled = canNext, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = if (canNext) c.ink2 else c.hairline)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = c.ink2) }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = c.ink, trackColor = c.hairline)
    }
}

@Composable
private fun BottomBar(selected: Tab, uncategorized: Int, onSelect: (Tab) -> Unit) {
    val c = FinTheme.colors
    Column(Modifier.background(MaterialTheme.colorScheme.background)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(64.dp)) {
            Tab.entries.forEach { t ->
                val on = t == selected
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    BadgedBox(
                        badge = {
                            if (t == Tab.Activity && uncategorized > 0) {
                                Badge(containerColor = c.accent, contentColor = MaterialTheme.colorScheme.background) { Text("$uncategorized") }
                            }
                        },
                    ) { Icon(t.icon, contentDescription = null, tint = if (on) c.ink else c.ink3) }
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = if (on) c.ink else c.ink3, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
