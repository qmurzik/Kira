package app.pulse.client.di

import app.pulse.client.BuildConfig
import app.pulse.client.data.api.SessionAuth
import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.data.db.AppDatabase
import app.pulse.client.data.mapper.fullName
import app.pulse.client.data.repo.FeedRepository
import app.pulse.client.data.repo.LongPollClient
import app.pulse.client.data.repo.MessagesRepository
import app.pulse.client.data.repo.NotificationsRepository
import app.pulse.client.data.repo.ProfileRepository
import app.pulse.client.data.repo.VideoRepository
import app.pulse.client.domain.model.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything bound to one signed-in account. Created/closed by [AppContainer]. */
class UserSession(val userId: Long, private val container: AppContainer) : SessionAuth {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()

    val api = VkApi(
        http = container.http,
        json = container.json,
        host = BuildConfig.VK_API_HOST,
        version = BuildConfig.VK_API_VERSION,
        auth = this,
    )

    private val dbLazy = lazy { AppDatabase.open(container.app, userId) }
    val db: AppDatabase by dbLazy

    val messages: MessagesRepository by lazy { MessagesRepository(api, db, container.json, userId) }
    val feed: FeedRepository by lazy { FeedRepository(api, db, container.json) }
    val profiles: ProfileRepository by lazy { ProfileRepository(api, userId) }
    val videos: VideoRepository by lazy { VideoRepository(api, userId) }
    val notifications: NotificationsRepository by lazy { NotificationsRepository(api) }

    private var longPoll: LongPollClient? = null

    /** Messages chosen for "Переслать" while the user picks the target dialog. */
    val forwardBuffer = MutableStateFlow<List<Message>>(emptyList())

    /** Total unread conversations, shown on the bottom bar. */
    val unreadCount: StateFlow<Int> get() = messages.unread

    @Synchronized
    fun onForeground() {
        val lp = longPoll ?: LongPollClient(api, container.longPollHttp, container.json, messages).also { longPoll = it }
        lp.start(scope)
    }

    @Synchronized
    fun onBackground() {
        longPoll?.stop()
    }

    fun refreshProfileAsync() {
        scope.launch { runCatching { refreshOwnProfile() } }
    }

    override fun token(): String? = container.accounts.get(userId)?.accessToken

    override suspend fun refreshAfterExpired(failedToken: String): Boolean = refreshLock.withLock {
        val account = container.accounts.get(userId) ?: return false
        // Another request already refreshed while we were waiting.
        if (account.accessToken != failedToken) return true
        if (account.refreshToken == null) {
            container.onSessionExpired(userId)
            return false
        }
        try {
            val renewed = container.vkId.refresh(account)
            container.accounts.upsert(renewed)
            true
        } catch (e: Exception) {
            container.onSessionExpired(userId)
            false
        }
    }

    private suspend fun refreshOwnProfile() {
        val me = api.request<List<UserDto>>("users.get", mapOf("fields" to "photo_100"))
        me.firstOrNull()?.let { u ->
            container.accounts.update(userId) { it.copy(name = u.fullName(), photo = u.photo100) }
        }
    }

    /**
     * [closeDb] only for sessions not attached to UI (background reply/sync): UI flows may still
     * be collecting from the database of the active session while navigation is torn down.
     */
    fun close(closeDb: Boolean = false) {
        longPoll?.stop()
        scope.cancel()
        if (closeDb && dbLazy.isInitialized()) runCatching { db.close() }
    }

    /** Called on logout: removes this account's cached data from disk. */
    fun deleteLocalData() {
        container.app.deleteDatabase(AppDatabase.fileName(userId))
    }
}
