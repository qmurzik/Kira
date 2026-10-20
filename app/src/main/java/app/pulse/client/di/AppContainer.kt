package app.pulse.client.di

import android.app.Application
import app.pulse.client.BuildConfig
import app.pulse.client.core.security.SecureStore
import app.pulse.client.data.auth.Account
import app.pulse.client.data.auth.AccountStore
import app.pulse.client.data.auth.VkIdAuth
import app.pulse.client.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Manual DI: app-wide singletons plus the [UserSession] of the active account.
 * Switching accounts closes the old session (its scope, Long Poll, database) and opens a new one.
 */
class AppContainer(val app: Application) {

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
        classDiscriminator = "_t"
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Same connection pool, longer read timeout for Long Poll (wait=25). */
    val longPollHttp: OkHttpClient = http.newBuilder()
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val secureStore = SecureStore(app)
    val accounts = AccountStore(secureStore, json)
    val settings = SettingsRepository(app)
    val vkId = VkIdAuth(http, json, BuildConfig.VK_ID_HOST)

    private val _session = MutableStateFlow(accounts.active?.let { openSession(it.userId) })
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    /** userId whose refresh token was rejected; UI offers to sign in again. */
    private val _expired = MutableStateFlow<Long?>(null)
    val expired: StateFlow<Long?> = _expired.asStateFlow()

    private fun openSession(userId: Long) = UserSession(userId, this)

    fun onLogin(account: Account) {
        accounts.upsert(account, makeActive = true)
        _expired.value = null
        switchTo(account.userId)
    }

    fun switchTo(userId: Long) {
        if (_session.value?.userId == userId) return
        accounts.setActive(userId)
        _session.value?.close()
        _session.value = openSession(userId)
    }

    suspend fun logout() {
        val s = _session.value ?: return
        accounts.get(s.userId)?.let { vkId.logout(it) }
        s.close()
        s.deleteLocalData()
        accounts.remove(s.userId)
        _session.value = accounts.active?.let { openSession(it.userId) }
    }

    internal fun onSessionExpired(userId: Long) {
        _expired.value = userId
    }
}
