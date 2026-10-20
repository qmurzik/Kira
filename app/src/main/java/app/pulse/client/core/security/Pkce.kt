package app.pulse.client.core.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/** PKCE (RFC 7636) helpers for the VK ID OAuth 2.1 code flow. */
object Pkce {
    private val random = SecureRandom()
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    /** 64 random bytes -> 86 chars of [A-Za-z0-9_-], inside the allowed 43..128 range. */
    fun newVerifier(): String {
        val bytes = ByteArray(64).also { random.nextBytes(it) }
        return Base64.encodeToString(bytes, FLAGS)
    }

    fun challengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, FLAGS)
    }

    fun newState(): String {
        val bytes = ByteArray(24).also { random.nextBytes(it) }
        return Base64.encodeToString(bytes, FLAGS)
    }
}
