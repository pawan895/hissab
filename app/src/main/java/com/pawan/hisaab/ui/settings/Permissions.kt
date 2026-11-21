package com.pawan.hisaab.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pawan.hisaab.ui.components.Hairline
import com.pawan.hisaab.ui.theme.FinTheme

object PermissionState {
    fun sms(ctx: Context) = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        .all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    fun overlay(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun notifications(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun battery(ctx: Context) =
        ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Phones known to kill background receivers unless "Autostart" is enabled. */
    val needsAutostart: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco", "oppo", "realme", "vivo", "oneplus", "iqoo", "huawei", "honor")
}

/**
 * Opens a system settings screen. Some phone makers remove or rename these screens,
 * so fall back to the app's own info page instead of crashing.
 */
private fun Context.openSettings(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        runCatching {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
}

/** Checklist of everything the app needs, re-checked every time the screen resumes. */
@SuppressLint("BatteryLife") // Personal sideloaded app: asking directly is the point.
@Composable
fun PermissionsChecklist() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }

    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val pkg = Uri.parse("package:${ctx.packageName}")

    // Re-checked whenever we come back from a system settings screen.
    val sms = remember(tick) { PermissionState.sms(ctx) }
    val overlay = remember(tick) { PermissionState.overlay(ctx) }
    val notifications = remember(tick) { PermissionState.notifications(ctx) }
    val battery = remember(tick) { PermissionState.battery(ctx) }

    Column {
        PermissionRow(
            title = "Read bank SMS",
            body = "Needed to log each debit and credit. Messages are read on the phone and never sent anywhere. " +
                "If Android says \"Restricted setting\", open App info → ⋮ → Allow restricted settings, then try again.",
            granted = sms,
        ) { smsLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)) }

        PermissionRow(
            title = "Display over other apps",
            body = "Shows the category card on top of GPay the moment a payment SMS arrives.",
            granted = overlay,
        ) { ctx.openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)) }

        PermissionRow(
            title = "Notifications",
            body = "Backup prompt with category buttons when the card can't be shown.",
            granted = notifications,
        ) {
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else ctx.openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        }

        PermissionRow(
            title = "Run in background",
            body = "Stops battery saver from blocking the SMS listener.",
            granted = battery,
        ) { ctx.openSettings(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)) }

        if (PermissionState.needsAutostart) {
            PermissionRow(
                title = "Autostart (${Build.MANUFACTURER})",
                body = "Turn on Autostart or \"Allow background activity\" in App info, or the popup may only appear while Hisaab is open.",
                granted = false,
            ) { ctx.openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)) }
        }
    }
}

@Composable
private fun PermissionRow(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
    val c = FinTheme.colors
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !granted, onClick = onGrant).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(body, style = MaterialTheme.typography.bodySmall, color = c.ink2)
            }
            Spacer(Modifier.width(16.dp))
            if (granted) {
                Icon(Icons.Default.Check, contentDescription = "Allowed", tint = c.ink3, modifier = Modifier.size(20.dp))
            } else {
                Text("Allow", style = MaterialTheme.typography.labelLarge, color = c.accent)
            }
        }
        Hairline()
    }
}
