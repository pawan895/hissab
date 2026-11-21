package com.pawan.hisaab.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pawan.hisaab.security.AppLock
import com.pawan.hisaab.ui.theme.FinTheme
import kotlin.math.ceil

@Composable
fun LockScreen(lock: AppLock, onBiometric: (() -> Unit)?) {
    val c = FinTheme.colors
    val haptics = LocalHapticFeedback.current
    val length = lock.pinLength
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    // Offer fingerprint/face once the screen is actually in front (the prompt is ignored
    // before onResume). Only once per lock, so cancelling to type the PIN doesn't loop.
    var prompted by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!prompted && onBiometric != null) {
            prompted = true
            onBiometric()
        }
    }

    fun press(digit: Char) {
        if (entered.length >= length) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        entered += digit
        error = null
        if (entered.length == length) {
            when (val r = lock.tryPin(entered)) {
                AppLock.Result.Ok -> {}
                is AppLock.Result.Wrong -> error = "Wrong PIN · ${r.attemptsLeft} ${if (r.attemptsLeft == 1) "try" else "tries"} left"
                is AppLock.Result.LockedOut -> {
                    val secs = ceil((r.untilMillis - System.currentTimeMillis()) / 1000.0).toInt().coerceAtLeast(1)
                    error = "Too many tries. Wait $secs seconds."
                }
            }
            if (error != null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                entered = ""
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Lock, contentDescription = null, tint = c.ink3, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(16.dp))
        Text("Enter PIN", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.semantics { contentDescription = "${entered.length} of $length digits entered" },
        ) {
            repeat(length) { i ->
                val filled = i < entered.length
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(if (filled) c.ink else MaterialTheme.colorScheme.background)
                        .border(1.5.dp, if (error != null) c.over else if (filled) c.ink else c.ink3, CircleShape),
                )
            }
        }
        Text(
            error ?: " ",
            style = MaterialTheme.typography.bodySmall,
            color = c.over,
            modifier = Modifier.padding(top = 16.dp),
        )
        Spacer(Modifier.weight(1f))

        val rows = listOf("123", "456", "789")
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                row.forEach { d -> Key(d.toString(), d.toString()) { press(d) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Box(Modifier.size(76.dp))
            Key("0", "0") { press('0') }
            Key("⌫", "Delete") {
                if (entered.isNotEmpty()) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    entered = entered.dropLast(1)
                }
            }
        }

        Box(Modifier.height(56.dp), contentAlignment = Alignment.Center) {
            if (onBiometric != null) {
                Text(
                    "Use fingerprint or face",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBiometric).padding(8.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Key(label: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(FinTheme.colors.tint)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.headlineMedium)
    }
}
