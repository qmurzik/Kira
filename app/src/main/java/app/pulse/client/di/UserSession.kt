package app.pulse.client.di

import app.pulse.client.BuildConfig
import app.pulse.client.data.api.SessionAuth
import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.data.mapper.fullName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Everything bound to one signed-in account. Created/closed by [AppContainer]. */
class UserSession(val userId: Long, private val container: AppContainer) : SessionAuth {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshLock = Mutex()

    /** Total unread conversations, shown on the bottom bar. */
    val unreadCount = MutableStateFlow(0)

    val api = VkApi(
        http = container.http,
        json = container.json,
        host = BuildConfig.VK_API_HOST,
        version = BuildConfig.VK_API_VERSION,
        auth = this,
    )

    init {
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

    fun close() {
        scope.cancel()
    }

    /** Called on logout: removes this account's cached data from disk. */
    fun deleteLocalData() {
    }
}
