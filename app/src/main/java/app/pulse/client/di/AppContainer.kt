package app.pulse.client.di

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.pulse.client.BuildConfig
import app.pulse.client.core.security.AppLock
import app.pulse.client.core.security.SecureStore
import app.pulse.client.data.auth.Account
import app.pulse.client.data.auth.AccountStore
import app.pulse.client.data.auth.VkIdAuth
import app.pulse.client.data.settings.SettingsRepository
import app.pulse.client.music.LocalAudioRepository
import app.pulse.client.music.MusicController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val appLock = AppLock(secureStore)
    val vkId = VkIdAuth(http, json, BuildConfig.VK_ID_HOST)

    /** Music is not tied to a VK account (local files), so it lives app-wide. */
    val music by lazy { MusicController(app) }
    val localAudio by lazy { LocalAudioRepository(app) }

    private val _session = MutableStateFlow(accounts.active?.let { openSession(it.userId) })
    val session: StateFlow<UserSession?> = _session.asStateFlow()

    /** userId whose refresh token was rejected; UI offers to sign in again. */
    private val _expired = MutableStateFlow<Long?>(null)
    val expired: StateFlow<Long?> = _expired.asStateFlow()

    /** VK ID redirect (vk<id>://vk.com/blank.html?...) delivered to MainActivity by the browser. */
    val authRedirect = MutableStateFlow<String?>(null)

    /** (userId, peerId) of a chat to open, set when a message notification is tapped. */
    val pendingChat = MutableStateFlow<Pair<Long, Long>?>(null)

    /** True while any activity of the app is started (ProcessLifecycleOwner). */
    @Volatile
    var isForeground: Boolean = false
        private set

    init {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                isForeground = true
                _session.value?.onForeground()
                appScope.launch { appLock.onForeground(settings.current().lockTimeoutSeconds) }
            }

            override fun onStop(owner: LifecycleOwner) {
                isForeground = false
                _session.value?.onBackground()
                appLock.onBackground()
            }
        })
    }

    private fun openSession(userId: Long) = UserSession(userId, this).also { s ->
        s.refreshProfileAsync()
        if (isForeground) s.onForeground()
    }

    /**
     * Session for background work (notification reply, sync). Returns the active session or a
     * temporary one for another signed-in account; the Boolean says whether the caller must close it.
     */
    fun sessionFor(userId: Long): Pair<UserSession, Boolean>? {
        _session.value?.takeIf { it.userId == userId }?.let { return it to false }
        if (accounts.get(userId) == null) return null
        return UserSession(userId, this) to true
    }

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
