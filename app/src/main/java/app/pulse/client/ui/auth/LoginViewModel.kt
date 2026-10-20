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

    fun start(clientIdInput: String, redirectInput: String) {
        val clientId = clientIdInput.trim()
        if (clientId.isEmpty() || !clientId.all { it.isDigit() }) {
            _state.value = State.Form(clientIdInput, redirectInput, "Укажите числовой ID вашего приложения VK ID")
            return
        }
        val redirect = redirectInput.trim().ifEmpty { container.vkId.defaultRedirectUri(clientId) }
        prefs.edit().putString("client_id", clientId).putString("redirect_uri", redirect).apply()
        _state.value = State.Web(container.vkId.newLogin(clientId, redirect))
    }

    /** Returns true if the WebView must not load [url] (it is our redirect). */
    fun onNavigate(url: String): Boolean {
        val web = _state.value as? State.Web ?: return false
        val callback = try {
            container.vkId.parseCallback(web.request, url) ?: return false
        } catch (e: VkIdAuth.AuthException) {
            _state.value = initialForm(e.message)
            return true
        }
        _state.value = State.Exchanging
        viewModelScope.launch {
            try {
                var account = container.vkId.exchange(web.request, callback)
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

    fun backToForm() {
        _state.value = initialForm()
    }
}
