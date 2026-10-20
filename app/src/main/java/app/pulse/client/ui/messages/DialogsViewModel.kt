package app.pulse.client.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Conversation
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DialogFilter(val label: String) {
    All("Все"),
    Unread("Непрочитанные"),
    Personal("Личные"),
    Chats("Беседы"),
    Groups("Сообщества"),
}

data class DialogsMeta(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val endReached: Boolean = false,
)

@OptIn(FlowPreview::class)
class DialogsViewModel(private val session: UserSession) : ViewModel() {

    private val repo = session.messages

    private val _filter = MutableStateFlow(DialogFilter.All)
    val filter: StateFlow<DialogFilter> = _filter.asStateFlow()

    private val _meta = MutableStateFlow(DialogsMeta())
    val meta: StateFlow<DialogsMeta> = _meta.asStateFlow()

    private val unreadServer = MutableStateFlow<List<Conversation>?>(null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Conversation>?>(null)
    val searchResults: StateFlow<List<Conversation>?> = _searchResults.asStateFlow()

    val typing = repo.typing

    val items: StateFlow<List<Conversation>> = combine(
        repo.observeConversations(), _filter, unreadServer,
    ) { cached, filter, unread ->
        when (filter) {
            DialogFilter.All -> cached
            DialogFilter.Unread -> unread ?: cached.filter { it.unreadCount > 0 || it.markedUnread }
            DialogFilter.Personal -> cached.filter { it.type == "user" }
            DialogFilter.Chats -> cached.filter { it.isChat }
            DialogFilter.Groups -> cached.filter { it.isGroup }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            _query.debounce(350).distinctUntilChanged().filter { it.isNotBlank() }.collect { q ->
                _searchResults.value = runCatching { repo.searchConversations(q) }.getOrDefault(emptyList())
            }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _meta.value = _meta.value.copy(refreshing = true, error = null)
            try {
                if (_filter.value == DialogFilter.Unread) {
                    unreadServer.value = repo.loadFiltered("unread", 0)
                }
                val n = repo.loadConversations(0)
                _meta.value = DialogsMeta(endReached = n == 0)
            } catch (e: Exception) {
                _meta.value = _meta.value.copy(refreshing = false, error = e.userMessage())
            }
        }
    }

    fun loadMore() {
        val m = _meta.value
        if (m.loading || m.refreshing || m.endReached || _filter.value != DialogFilter.All) return
        loadJob = viewModelScope.launch {
            _meta.value = m.copy(loading = true)
            try {
                val n = repo.loadConversations(items.value.size)
                _meta.value = _meta.value.copy(loading = false, endReached = n == 0)
            } catch (e: Exception) {
                _meta.value = _meta.value.copy(loading = false, error = e.userMessage())
            }
        }
    }

    fun setFilter(f: DialogFilter) {
        _filter.value = f
        if (f == DialogFilter.Unread) {
            viewModelScope.launch {
                unreadServer.value = runCatching { repo.loadFiltered("unread", 0) }.getOrNull()
            }
        }
    }

    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) _searchResults.value = null
    }
}
