package com.pawan.hisaab.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small synchronous settings store; read from the SMS receiver, so no suspend needed. */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _popupEnabled = MutableStateFlow(prefs.getBoolean(KEY_POPUP, true))
    val popupEnabled: StateFlow<Boolean> = _popupEnabled

    private val _autoCategorize = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoCategorize: StateFlow<Boolean> = _autoCategorize

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone

    fun setPopupEnabled(v: Boolean) { prefs.edit { putBoolean(KEY_POPUP, v) }; _popupEnabled.value = v }
    fun setAutoCategorize(v: Boolean) { prefs.edit { putBoolean(KEY_AUTO, v) }; _autoCategorize.value = v }
    fun setOnboardingDone(v: Boolean) { prefs.edit { putBoolean(KEY_ONBOARDED, v) }; _onboardingDone.value = v }

    /** Inbox messages newer than this have been scanned. 0 = never scanned. */
    var lastInboxScan: Long
        get() = prefs.getLong(KEY_LAST_SCAN, 0L)
        set(v) = prefs.edit { putLong(KEY_LAST_SCAN, v) }

    companion object {
        /** Auto-file a merchant once you've picked its category this many times. */
        const val AUTO_CATEGORIZE_AFTER = 1

        private const val KEY_POPUP = "popup_enabled"
        private const val KEY_AUTO = "auto_categorize"
        private const val KEY_ONBOARDED = "onboarding_done"
        private const val KEY_LAST_SCAN = "last_inbox_scan"
    }
}
