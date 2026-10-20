package app.pulse.client.core.security

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PIN lock. The PIN exists only as a PBKDF2 hash inside [SecureStore]; "PIN enabled" is the
 * presence of that hash, so the lock state is known synchronously at cold start (no flash of content).
 */
class AppLock(private val secure: SecureStore) {

    private val _locked = MutableStateFlow(hasPin())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundAt = 0L

    fun hasPin(): Boolean = secure.getString(KEY_PIN) != null

    fun setPin(pin: String) {
        secure.putString(KEY_PIN, PinHasher.hash(pin))
    }

    fun clearPin() {
        secure.remove(KEY_PIN)
        _locked.value = false
    }

    /** Returns true and unlocks if [pin] matches. */
    fun verify(pin: String): Boolean {
        val stored = secure.getString(KEY_PIN) ?: return true
        val ok = PinHasher.verify(pin, stored)
        if (ok) _locked.value = false
        return ok
    }

    fun unlockedByBiometrics() {
        _locked.value = false
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun onForeground(timeoutSeconds: Int) {
        if (!hasPin() || backgroundAt == 0L) return
        if (SystemClock.elapsedRealtime() - backgroundAt >= timeoutSeconds * 1000L) _locked.value = true
    }

    private companion object {
        const val KEY_PIN = "pin_hash"
    }
}
