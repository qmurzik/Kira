package app.pulse.client.core.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2 hash for the app-lock PIN. The result itself is kept in [SecureStore]. */
object PinHasher {
    private const val ITERATIONS = 20_000

    fun hash(pin: String, salt: ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        // PBKDF2WithHmacSHA1 is the variant available on every API level >= 21.
        val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        return Base64.encodeToString(salt, Base64.NO_WRAP) + ":" + Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verify(pin: String, stored: String): Boolean {
        val salt = Base64.decode(stored.substringBefore(':'), Base64.NO_WRAP)
        val expected = hash(pin, salt)
        return constantTimeEquals(expected, stored)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].code xor b[i].code)
        return r == 0
    }
}
