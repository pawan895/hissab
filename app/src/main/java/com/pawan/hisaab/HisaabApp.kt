package com.pawan.hisaab

import android.app.Application
import android.content.Context
import com.pawan.hisaab.data.AppSettings
import com.pawan.hisaab.data.DataTransfer
import com.pawan.hisaab.data.FinanceRepository
import com.pawan.hisaab.data.db.AppDatabase
import com.pawan.hisaab.popup.CategoryNotification
import com.pawan.hisaab.security.AppLock
import com.pawan.hisaab.sms.InboxBackfill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val db = AppDatabase.build(context)
    val settings = AppSettings(context)
    val repo = FinanceRepository(db, settings)
    val transfer = DataTransfer(db)
    val backfill = InboxBackfill(context, repo, settings)
    val lock = AppLock(context)
}

class HisaabApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CategoryNotification.createChannel(this)
        container.appScope.launch {
            container.repo.ensureSeeded()
            // Sort anything still unsorted from merchants that are now known.
            container.repo.fileKnownMerchants()
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as HisaabApp).container
