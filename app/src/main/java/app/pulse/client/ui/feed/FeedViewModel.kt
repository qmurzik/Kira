package app.pulse.client.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.pulse.client.data.api.userMessage
import app.pulse.client.data.repo.FeedItem
import app.pulse.client.data.repo.FeedKind
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Post
import app.pulse.client.domain.model.StoryGroup
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** Shared by the feed and stories screens: stories are kept here so the viewer can reuse them. */
class FeedViewModel(private val session: UserSession) : ViewModel() {

    private val repo = session.feed

    private val _kind = MutableStateFlow(FeedKind.News)
    val kind: StateFlow<FeedKind> = _kind.asStateFlow()

    val news: Flow<PagingData<FeedItem>> = repo.pager(FeedKind.News).cachedIn(viewModelScope)
    val recommended: Flow<PagingData<FeedItem>> by lazy { repo.pager(FeedKind.Recommended).cachedIn(viewModelScope) }

    private val _stories = MutableStateFlow<List<StoryGroup>>(emptyList())
    val stories: StateFlow<List<StoryGroup>> = _stories.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        loadStories()
    }

    fun loadStories() {
        viewModelScope.launch {
            // Stories need the "stories" scope; without it the row is simply hidden.
            _stories.value = runCatching { repo.stories() }.getOrDefault(emptyList())
        }
    }

    fun setKind(k: FeedKind) {
        _kind.value = k
    }

    fun like(post: Post) {
        viewModelScope.launch {
            runCatching { repo.toggleLike(post) }.onFailure { _messages.send(it.userMessage()) }
        }
    }

    fun repost(post: Post, message: String?) {
        viewModelScope.launch {
            runCatching { repo.repost(post, message) }
                .onSuccess { _messages.send("Запись опубликована у вас на стене") }
                .onFailure { _messages.send(it.userMessage()) }
        }
    }

    fun markSeen(groupIndex: Int, storyIndex: Int) {
        val g = _stories.value.getOrNull(groupIndex) ?: return
        val s = g.stories.getOrNull(storyIndex) ?: return
        if (s.seen) return
        viewModelScope.launch { repo.markStorySeen(s) }
        _stories.value = _stories.value.mapIndexed { gi, group ->
            if (gi != groupIndex) group else group.copy(stories = group.stories.mapIndexed { si, st -> if (si == storyIndex) st.copy(seen = true) else st })
        }
    }
}
