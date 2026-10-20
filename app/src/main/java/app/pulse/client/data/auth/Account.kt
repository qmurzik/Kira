package app.pulse.client.data.auth

import kotlinx.serialization.Serializable

/** One signed-in VK ID session. Persisted only inside [AccountStore] (encrypted). */
@Serializable
data class Account(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String? = null,
    val deviceId: String,
    val clientId: String,
    val redirectUri: String,
    /** Unix millis; 0 = unknown. */
    val expiresAt: Long = 0,
    val scope: String = "",
    val name: String = "",
    val photo: String? = null,
) {
    override fun toString(): String = "Account(userId=$userId, name=$name)" // never print tokens
}
