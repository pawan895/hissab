package com.pawan.hisaab.popup

import android.content.Context
import android.provider.Settings
import com.pawan.hisaab.container
import com.pawan.hisaab.data.IngestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Overlay card when allowed, otherwise a heads-up notification with category buttons. */
object PopupLauncher {
    suspend fun show(context: Context, result: IngestResult) {
        val shown = Settings.canDrawOverlays(context) && withContext(Dispatchers.Main) {
            OverlayController.enqueue(context, PopupRequest(result.txn.id, result.autoCategorized))
        }
        if (!shown) {
            val repo = context.container.repo
            val auto = if (result.autoCategorized) result.txn.categoryId?.let { repo.category(it) } else null
            CategoryNotification.post(context, result.txn, repo.suggestions(result.txn, 3), auto)
        }
    }
}
