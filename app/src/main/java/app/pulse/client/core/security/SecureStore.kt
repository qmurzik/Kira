package app.pulse.client.core.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.annotation.RequiresApi
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

/**
 * Small encrypted key-value store for session secrets (tokens, PIN hash).
 *
 * - API 23+: AES-256-GCM key generated inside AndroidKeyStore (never leaves it).
 * - API 21-22: AndroidKeyStore cannot hold AES keys, so a random AES key is wrapped
 *   with an RSA key pair that lives in AndroidKeyStore; only the wrapped form is stored.
 *
 * Values are stored as base64(iv || ciphertext). Nothing here is ever logged.
 */
class SecureStore(context: Context, name: String = "pulse_secure") {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    private val crypto: Crypto = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        KeystoreAes()
    } else {
        RsaWrappedAes(context.applicationContext, prefs)
    }

    @Synchronized
    fun getString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return runCatching { crypto.decrypt(stored) }.getOrNull()
    }

    @Synchronized
    fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, crypto.encrypt(value))
        editor.apply()
    }

    @Synchronized
    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    private interface Crypto {
        fun encrypt(plain: String): String
        fun decrypt(encoded: String): String
    }

    private abstract class AesGcm : Crypto {
        abstract fun key(): SecretKey

        override fun encrypt(plain: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val iv = cipher.iv
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            val out = ByteArray(iv.size + ct.size)
            System.arraycopy(iv, 0, out, 0, iv.size)
            System.arraycopy(ct, 0, out, iv.size, ct.size)
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        override fun decrypt(encoded: String): String {
            val data = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
            val plain = cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
            return String(plain, Charsets.UTF_8)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private class KeystoreAes : AesGcm() {
        private var cached: SecretKey? = null

        override fun key(): SecretKey {
            cached?.let { return it }
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existing = (ks.getEntry(AES_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
                this.init(
                    KeyGenParameterSpec.Builder(
                        AES_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
                generateKey()
            }
            cached = key
            return key
        }
    }

    private class RsaWrappedAes(
        private val context: Context,
        private val prefs: SharedPreferences
    ) : AesGcm() {
        private var cached: SecretKey? = null

        override fun key(): SecretKey {
            cached?.let { return it }
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!ks.containsAlias(RSA_ALIAS)) generateRsa()
            val entry = ks.getEntry(RSA_ALIAS, null) as KeyStore.PrivateKeyEntry
            val wrapped = prefs.getString(WRAPPED_KEY, null)
            val key = if (wrapped != null) {
                SecretKeySpec(rsa(Cipher.DECRYPT_MODE, entry.privateKey, null, Base64.decode(wrapped, Base64.NO_WRAP)), "AES")
            } else {
                val raw = ByteArray(32).also { SecureRandom().nextBytes(it) }
                val enc = rsa(Cipher.ENCRYPT_MODE, null, entry.certificate.publicKey, raw)
                prefs.edit().putString(WRAPPED_KEY, Base64.encodeToString(enc, Base64.NO_WRAP)).apply()
                SecretKeySpec(raw, "AES")
            }
            cached = key
            return key
        }

        private fun rsa(mode: Int, priv: PrivateKey?, pub: PublicKey?, input: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL")
            if (mode == Cipher.ENCRYPT_MODE) cipher.init(mode, pub) else cipher.init(mode, priv)
            return cipher.doFinal(input)
        }

        @Suppress("DEPRECATION")
        private fun generateRsa() {
            val start = Calendar.getInstance()
            val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            val spec = KeyPairGeneratorSpec.Builder(context)
                .setAlias(RSA_ALIAS)
                .setSubject(X500Principal("CN=$RSA_ALIAS"))
                .setSerialNumber(BigInteger.ONE)
                .setStartDate(start.time)
                .setEndDate(end.time)
                .build()
            KeyPairGenerator.getInstance("RSA", ANDROID_KEYSTORE).apply {
                this.initialize(spec)
                generateKeyPair()
            }
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val AES_ALIAS = "pulse_master_aes"
        const val RSA_ALIAS = "pulse_master_rsa"
        const val WRAPPED_KEY = "__wrapped_aes"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
