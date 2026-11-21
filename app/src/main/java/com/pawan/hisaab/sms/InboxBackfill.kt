package com.pawan.hisaab.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pawan.hisaab.data.AppSettings
import com.pawan.hisaab.data.FinanceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Reads bank SMS already in the inbox: on first run (history import) and on every app
 * open, to catch anything missed while a battery manager had the receiver blocked.
 * Duplicates are dropped by the SMS hash, so overlapping scans are safe.
 */
class InboxBackfill(
    private val context: Context,
    private val repo: FinanceRepository,
    private val settings: AppSettings,
) {
    fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun scanMonths(months: Long): Int =
        scan(ZonedDateTime.now().minusMonths(months).toInstant().toEpochMilli())

    suspend fun scanSinceLast(): Int {
        val last = settings.lastInboxScan
        if (last == 0L) return 0
        return scan(last - TimeUnit.DAYS.toMillis(1))
    }

    private suspend fun scan(since: Long): Int = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext 0
        repo.ensureSeeded()
        val startedAt = System.currentTimeMillis()
        var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            val addr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext()) {
                val text = c.getString(body) ?: continue
                if (repo.ingestSms(c.getString(addr).orEmpty(), text, c.getLong(date)) != null) added++
            }
        }
        settings.lastInboxScan = startedAt
        added
    }
}
