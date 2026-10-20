package app.pulse.client.data.auth

import android.net.Uri
import app.pulse.client.core.security.Pkce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * VK ID (OAuth 2.1 + PKCE) as described in the VK ID "authorization without SDK" docs:
 *  1. open https://<host>/authorize?response_type=code&... in a WebView;
 *  2. intercept the redirect to [LoginRequest.redirectUri] and read code/state/device_id;
 *  3. POST https://<host>/oauth2/auth grant_type=authorization_code -> access + refresh token;
 *  4. on VK API error 5 POST the same endpoint with grant_type=refresh_token.
 *
 * Only the user's own registered app id is used. Tokens never reach logs.
 */
class VkIdAuth(private val http: OkHttpClient, private val json: Json, private val host: String) {

    data class LoginRequest(
        val clientId: String,
        val redirectUri: String,
        val scope: String,
        val verifier: String,
        val state: String,
        val url: String,
    )

    data class Callback(val code: String, val state: String, val deviceId: String)

    @Serializable
    data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long = 0,
        @SerialName("user_id") val userId: Long = 0,
        val scope: String? = null,
        val state: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    )

    class AuthException(message: String) : Exception(message)

    fun defaultRedirectUri(clientId: String) = "vk$clientId://vk.com/blank.html"

    fun newLogin(clientId: String, redirectUri: String, scope: String = DEFAULT_SCOPE): LoginRequest {
        val verifier = Pkce.newVerifier()
        val state = Pkce.newState()
        val url = Uri.Builder()
            .scheme("https")
            .authority(host)
            .path("authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("scope", scope)
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", Pkce.challengeS256(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("lang_id", "0")
            .build()
            .toString()
        return LoginRequest(clientId, redirectUri, scope, verifier, state, url)
    }

    /** Returns null if [url] is not our redirect; throws if it is an error redirect. */
    fun parseCallback(request: LoginRequest, url: String): Callback? {
        if (!url.startsWith(request.redirectUri.substringBefore('?'))) return null
        val uri = Uri.parse(url)
        // Some providers put params in the fragment; accept both.
        val params = uri.encodedQuery ?: uri.encodedFragment ?: ""
        val p = Uri.parse("x://x?$params")
        p.getQueryParameter("error")?.let { err ->
            throw AuthException(p.getQueryParameter("error_description") ?: err)
        }
        val code = p.getQueryParameter("code") ?: throw AuthException("В ответе нет кода авторизации")
        val state = p.getQueryParameter("state").orEmpty()
        if (state != request.state) throw AuthException("Неверный state: возможна подмена ответа")
        val deviceId = p.getQueryParameter("device_id") ?: throw AuthException("В ответе нет device_id")
        return Callback(code, state, deviceId)
    }

    suspend fun exchange(request: LoginRequest, callback: Callback): Account {
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("code_verifier", request.verifier)
            .add("redirect_uri", request.redirectUri)
            .add("code", callback.code)
            .add("client_id", request.clientId)
            .add("device_id", callback.deviceId)
            .add("state", Pkce.newState())
            .build()
        val r = post("oauth2/auth", body)
        val access = r.accessToken ?: throw AuthException(r.errorDescription ?: r.error ?: "Пустой ответ VK ID")
        return Account(
            userId = r.userId,
            accessToken = access,
            refreshToken = r.refreshToken,
            deviceId = callback.deviceId,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            expiresAt = if (r.expiresIn > 0) System.currentTimeMillis() + r.expiresIn * 1000 else 0,
            scope = r.scope ?: request.scope,
        )
    }

    /** Refresh tokens are single-use: the caller must persist the returned account. */
    suspend fun refresh(account: Account): Account {
        val refresh = account.refreshToken ?: throw AuthException("Нет refresh token")
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", account.clientId)
            .add("device_id", account.deviceId)
            .add("state", Pkce.newState())
            .build()
        val r = post("oauth2/auth", body)
        val access = r.accessToken ?: throw AuthException(r.errorDescription ?: r.error ?: "Не удалось обновить сессию")
        return account.copy(
            accessToken = access,
            refreshToken = r.refreshToken ?: account.refreshToken,
            expiresAt = if (r.expiresIn > 0) System.currentTimeMillis() + r.expiresIn * 1000 else 0,
        )
    }

    /** Best effort server-side logout; local data is wiped regardless of the result. */
    suspend fun logout(account: Account) {
        runCatching {
            post(
                "oauth2/logout",
                FormBody.Builder()
                    .add("client_id", account.clientId)
                    .add("access_token", account.accessToken)
                    .build()
            )
        }
    }

    private suspend fun post(path: String, body: FormBody): TokenResponse = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("https://$host/$path").post(body).build()
        http.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            runCatching { json.decodeFromString(TokenResponse.serializer(), text) }
                .getOrElse { throw AuthException("VK ID: HTTP ${resp.code}") }
        }
    }

    companion object {
        /**
         * Space separated VK ID scopes. The set actually granted is limited by what is
         * enabled for the app in the VK ID cabinet; see docs/API-COVERAGE.md.
         */
        const val DEFAULT_SCOPE =
            "vkid.personal_info friends wall photos video stories groups notifications messages docs status"
    }
}
