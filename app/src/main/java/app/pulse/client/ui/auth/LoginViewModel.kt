package app.pulse.client.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pulse.client.BuildConfig
import app.pulse.client.data.api.SessionAuth
import app.pulse.client.data.api.VkApi
import app.pulse.client.data.api.dto.UserDto
import app.pulse.client.data.auth.VkIdAuth
import app.pulse.client.data.mapper.fullName
import app.pulse.client.di.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    sealed interface State {
        data class Form(val clientId: String, val redirectUri: String, val error: String? = null) : State
        data class Web(val request: VkIdAuth.LoginRequest) : State

        /** Login page opened in the system browser; waiting for the vk<ID>:// redirect. */
        data class Browser(val request: VkIdAuth.LoginRequest) : State
        data object Exchanging : State
        data object Done : State
    }

    // The app id is public configuration, not a secret: plain prefs are fine.
    private val prefs = container.app.getSharedPreferences("login_config", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(initialForm())
    val state: StateFlow<State> = _state.asStateFlow()

    private fun initialForm(error: String? = null): State.Form {
        val id = prefs.getString("client_id", null) ?: BuildConfig.VK_CLIENT_ID
        val redirect = prefs.getString("redirect_uri", null)
            ?: if (id.isNotBlank()) container.vkId.defaultRedirectUri(id) else ""
        return State.Form(id, redirect, error)
    }

    /** The browser can only return to the scheme baked into the manifest at build time. */
    val browserLoginAvailable: Boolean get() = BuildConfig.VK_CLIENT_ID.isNotBlank()

    fun start(clientIdInput: String, redirectInput: String, inBrowser: Boolean = false) {
        val clientId = clientIdInput.trim()
        if (clientId.isEmpty() || !clientId.all { it.isDigit() }) {
            _state.value = State.Form(clientIdInput, redirectInput, "Укажите числовой ID вашего приложения VK ID")
            return
        }
        if (inBrowser && clientId != BuildConfig.VK_CLIENT_ID) {
            _state.value = State.Form(
                clientIdInput,
                redirectInput,
                "Вход через браузер в этой сборке работает только для приложения ${BuildConfig.VK_CLIENT_ID.ifBlank { "—" }}",
            )
            return
        }
        val redirect = redirectInput.trim().ifEmpty { container.vkId.defaultRedirectUri(clientId) }
        prefs.edit().putString("client_id", clientId).putString("redirect_uri", redirect).apply()
        val request = container.vkId.newLogin(clientId, redirect)
        savePending(request)
        _state.value = if (inBrowser) State.Browser(request) else State.Web(request)
    }

    /** Switch from the WebView to the system browser with the same PKCE request. */
    fun switchToBrowser() {
        val web = _state.value as? State.Web ?: return
        _state.value = State.Browser(web.request)
    }

    /** Returns true if the WebView must not load [url] (it is our redirect). */
    fun onNavigate(url: String): Boolean {
        val request = (_state.value as? State.Web)?.request ?: return false
        return handle(request, url)
    }

    /** Redirect delivered by the browser to MainActivity. Survives process death via [loadPending]. */
    fun onBrowserRedirect(url: String) {
        val request = (_state.value as? State.Browser)?.request
            ?: (_state.value as? State.Web)?.request
            ?: loadPending()
            ?: run {
                _state.value = initialForm("Запрос входа устарел, начните заново")
                return
            }
        handle(request, url)
    }

    private fun handle(request: VkIdAuth.LoginRequest, url: String): Boolean {
        val callback = try {
            container.vkId.parseCallback(request, url) ?: return false
        } catch (e: VkIdAuth.AuthException) {
            clearPending()
            _state.value = initialForm(e.message)
            return true
        }
        clearPending()
        _state.value = State.Exchanging
        viewModelScope.launch {
            try {
                var account = container.vkId.exchange(request, callback)
                // Fill name/avatar for the account switcher.
                val token = account.accessToken
                val api = VkApi(container.http, container.json, BuildConfig.VK_API_HOST, BuildConfig.VK_API_VERSION,
                    object : SessionAuth {
                        override fun token() = token
                        override suspend fun refreshAfterExpired(failedToken: String) = false
                    })
                runCatching { api.request<List<UserDto>>("users.get", mapOf("fields" to "photo_100")) }
                    .getOrNull()?.firstOrNull()?.let { u ->
                        account = account.copy(
                            userId = if (account.userId == 0L) u.id else account.userId,
                            name = u.fullName(),
                            photo = u.photo100,
                        )
                    }
                container.onLogin(account)
                _state.value = State.Done
            } catch (e: Exception) {
                _state.value = initialForm(e.message ?: "Ошибка входа")
            }
        }
        return true
    }

    fun backToForm(error: String? = null) {
        clearPending()
        _state.value = initialForm(error)
    }

    // PKCE verifier and state are kept encrypted until the redirect comes back.
    private fun savePending(r: VkIdAuth.LoginRequest) {
        container.secureStore.putString(KEY_PENDING, container.json.encodeToString(VkIdAuth.LoginRequest.serializer(), r))
    }

    private fun loadPending(): VkIdAuth.LoginRequest? = container.secureStore.getString(KEY_PENDING)?.let {
        runCatching { container.json.decodeFromString(VkIdAuth.LoginRequest.serializer(), it) }.getOrNull()
    }

    private fun clearPending() = container.secureStore.remove(KEY_PENDING)

    private companion object {
        const val KEY_PENDING = "pending_login"
    }
}
