package com.pawan.hisaab.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.pawan.hisaab.container
import com.pawan.hisaab.popup.PopupLauncher
import kotlinx.coroutines.launch

/** Fires for every incoming SMS, even when the app is not running. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return
        // Long SMS arrive as several parts; stitch them back together per sender.
        val bySender = messages.groupBy { it.displayOriginatingAddress.orEmpty() }

        val app = context.applicationContext
        val c = app.container
        val pending = goAsync()
        c.appScope.launch {
            try {
                c.repo.ensureSeeded()
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
                    val result = c.repo.ingestSms(sender, body, System.currentTimeMillis()) ?: continue
                    if (c.settings.popupEnabled.value) PopupLauncher.show(app, result)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
