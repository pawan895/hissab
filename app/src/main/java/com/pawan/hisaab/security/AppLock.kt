package com.pawan.hisaab.security

import android.content.Context
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PIN + biometric lock for opening the app. The PIN is stored only as a salted
 * PBKDF2 hash. Unlock state lives in memory, so a fresh process always starts locked.
 */
class AppLock(context: Context) {
    sealed interface Result {
        data object Ok : Result
        data class Wrong(val attemptsLeft: Int) : Result
        data class LockedOut(val untilMillis: Long) : Result
    }

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled

    private val _biometric = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC, true))
    val biometric: StateFlow<Boolean> = _biometric

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked

    private var backgroundedAt = 0L

    init {
        if (!prefs.contains(KEY_HASH)) storePin(DEFAULT_PIN)
    }

    val pinLength: Int get() = prefs.getInt(KEY_LENGTH, DEFAULT_PIN.length)

    fun biometricAvailable(): Boolean =
        BiometricManager.from(appContext).canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Checks a PIN, counting failures; also used to confirm sensitive settings changes. */
    fun tryPin(pin: String): Result {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        if (now < lockedUntil) return Result.LockedOut(lockedUntil)
        if (matches(pin)) {
            prefs.edit { putInt(KEY_FAILS, 0) }
            _locked.value = false
            return Result.Ok
        }
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        if (fails >= MAX_ATTEMPTS) {
            val until = now + LOCKOUT_MS
            prefs.edit { putInt(KEY_FAILS, 0); putLong(KEY_LOCKOUT_UNTIL, until) }
            return Result.LockedOut(until)
        }
        prefs.edit { putInt(KEY_FAILS, fails) }
        return Result.Wrong(MAX_ATTEMPTS - fails)
    }

    fun promptBiometric(activity: FragmentActivity) {
        if (!_biometric.value || !biometricAvailable()) return
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                prefs.edit { putInt(KEY_FAILS, 0) }
                _locked.value = false
            }
        }
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Hisaab")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build(),
        )
    }

    fun setEnabled(v: Boolean) {
        prefs.edit { putBoolean(KEY_ENABLED, v) }
        _enabled.value = v
        if (!v) _locked.value = false
    }

    fun setBiometric(v: Boolean) {
        prefs.edit { putBoolean(KEY_BIOMETRIC, v) }
        _biometric.value = v
    }

    fun changePin(newPin: String) {
        require(newPin.length in 4..6 && newPin.all { it.isDigit() })
        storePin(newPin)
    }

    /** First-run setup happens before any PIN is known to be needed. */
    fun unlockForSetup() { _locked.value = false }

    fun onBackground() { backgroundedAt = System.currentTimeMillis() }

    fun onForeground() {
        if (_enabled.value && backgroundedAt != 0L && System.currentTimeMillis() - backgroundedAt > GRACE_MS) {
            _locked.value = true
        }
        backgroundedAt = 0L
    }

    private fun storePin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            putInt(KEY_LENGTH, pin.length)
        }
    }

    private fun matches(pin: String): Boolean {
        val salt = Base64.decode(prefs.getString(KEY_SALT, null) ?: return false, Base64.NO_WRAP)
        val expected = Base64.decode(prefs.getString(KEY_HASH, null) ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(pin, salt), expected)
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, 20_000, 256))
            .encoded

    companion object {
        /** Initial PIN on a fresh install; change it in Settings → Change PIN. */
        const val DEFAULT_PIN = "0809"

        /** Coming back within this window (e.g. from the file picker) doesn't re-lock. */
        private const val GRACE_MS = 30_000L
        private const val MAX_ATTEMPTS = 5
        private const val LOCKOUT_MS = 30_000L

        private const val KEY_ENABLED = "enabled"
        private const val KEY_BIOMETRIC = "biometric"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_LENGTH = "pin_length"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCKOUT_UNTIL = "lockout_until"
    }
}
