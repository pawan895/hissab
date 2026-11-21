package com.pawan.hisaab.popup

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pawan.hisaab.MainActivity
import com.pawan.hisaab.R
import com.pawan.hisaab.container
import com.pawan.hisaab.data.db.Category
import com.pawan.hisaab.data.db.Direction
import com.pawan.hisaab.data.db.Txn
import com.pawan.hisaab.sms.MerchantNormalizer
import com.pawan.hisaab.util.Money
import kotlinx.coroutines.launch

/** Fallback when "Display over other apps" is off: a heads-up notification with category buttons. */
object CategoryNotification {
    private const val CHANNEL = "txn_category"
    const val EXTRA_TXN_ID = "txn_id"
    const val EXTRA_CATEGORY_ID = "category_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Categorize payments", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Asks for a category when a bank SMS arrives"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // guarded by areNotificationsEnabled()
    fun post(context: Context, txn: Txn, suggestions: List<Category>, autoCategory: Category?) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val verb = if (txn.direction == Direction.DEBIT) "paid to" else "received from"
        val title = "${Money.format(txn.amountPaise)} $verb ${MerchantNormalizer.displayName(txn.merchantRaw)}"
        val where = listOf(txn.bank, txn.accountLast4.takeIf { it.isNotBlank() }?.let { "••$it" }.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" ")

        val open = PendingIntent.getActivity(
            context, txn.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TXN_ID, txn.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rupee)
            .setContentTitle(title)
            .setContentText(if (autoCategory != null) "Filed under ${autoCategory.name} · tap to change" else "$where · pick a category")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(open)
            .setAutoCancel(true)

        if (autoCategory == null) {
            suggestions.take(3).forEachIndexed { i, c ->
                val pi = PendingIntent.getBroadcast(
                    context, (txn.id * 10 + i).toInt(),
                    Intent(context, NotificationActionReceiver::class.java)
                        .putExtra(EXTRA_TXN_ID, txn.id)
                        .putExtra(EXTRA_CATEGORY_ID, c.id),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                builder.addAction(0, "${c.emoji} ${c.name}", pi)
            }
        }
        nm.notify(txn.id.toInt(), builder.build())
    }

    fun cancel(context: Context, txnId: Long) = NotificationManagerCompat.from(context).cancel(txnId.toInt())
}

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val txnId = intent.getLongExtra(CategoryNotification.EXTRA_TXN_ID, -1)
        val categoryId = intent.getLongExtra(CategoryNotification.EXTRA_CATEGORY_ID, -1)
        if (txnId < 0 || categoryId < 0) return
        val pending = goAsync()
        val c = context.applicationContext.container
        c.appScope.launch {
            try {
                c.repo.categorize(txnId, categoryId)
                CategoryNotification.cancel(context, txnId)
            } finally {
                pending.finish()
            }
        }
    }
}
