package app.pulse.client.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Owner
import app.pulse.client.domain.model.Post
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.ListRow
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.feed.PostCard
import app.pulse.client.ui.navigation.LocalNavigator
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) { People("Люди"), Groups("Сообщества"), Posts("Новости") }

sealed interface SearchResult {
    data object Idle : SearchResult
    data object Loading : SearchResult
    data class Owners(val items: List<Owner>) : SearchResult
    data class Posts(val items: List<Post>) : SearchResult
    data class Error(val message: String) : SearchResult
}

@OptIn(FlowPreview::class)
class SearchViewModel(private val session: UserSession) : ViewModel() {
    val query = MutableStateFlow("")
    val tab = MutableStateFlow(SearchTab.People)
    val result = MutableStateFlow<SearchResult>(SearchResult.Idle)

    init {
        viewModelScope.launch {
            combine(query.debounce(400), tab) { q, t -> q.trim() to t }
                .distinctUntilChanged()
                .collect { (q, t) -> run(q, t) }
        }
    }

    private suspend fun run(q: String, t: SearchTab) {
        if (q.isEmpty()) {
            result.value = SearchResult.Idle
            return
        }
        result.value = SearchResult.Loading
        result.value = try {
            when (t) {
                SearchTab.People -> SearchResult.Owners(session.profiles.searchPeople(q, 0))
                SearchTab.Groups -> SearchResult.Owners(session.profiles.searchGroups(q, 0))
                SearchTab.Posts -> SearchResult.Posts(session.feed.searchPosts(q, null).first)
            }
        } catch (e: Exception) {
            SearchResult.Error(e.userMessage())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen() {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel { SearchViewModel(session) }
    val query by vm.query.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val actions = remember(nav) {
        AttachmentActions(onPhoto = nav::photo, onVideo = nav::video, onWall = { o, p -> nav.post(o, p) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        placeholder = { Text("Поиск") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                actions = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, "Очистить") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryScrollableTabRow(selectedTabIndex = tab.ordinal, edgePadding = 8.dp) {
                SearchTab.entries.forEach { t ->
                    Tab(selected = t == tab, onClick = { vm.tab.value = t }, text = { Text(t.label) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                when (val r = result) {
                    SearchResult.Idle -> MessageBox("Введите запрос")
                    SearchResult.Loading -> LoadingBox()
                    is SearchResult.Error -> ErrorBox(r.message, null)
                    is SearchResult.Owners -> if (r.items.isEmpty()) MessageBox("Ничего не найдено") else LazyColumn {
                        items(r.items, key = { it.id }, contentType = { "owner" }) { o ->
                            ListRow(
                                title = o.name,
                                subtitle = if (o.isGroup) "Сообщество" else Format.lastSeen(o.online, o.lastSeen),
                                leading = { Avatar(o.photo, o.name, 48.dp, online = o.online) },
                                modifier = Modifier.clickable { nav.profile(o.id) },
                            )
                        }
                    }
                    is SearchResult.Posts -> if (r.items.isEmpty()) MessageBox("Ничего не найдено") else LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(r.items, key = { it.key }, contentType = { "post" }) { p ->
                            PostCard(
                                post = p,
                                actions = actions,
                                onAuthor = nav::profile,
                                onOpen = { nav.post(p.ownerId, p.id) },
                                onLike = { nav.post(p.ownerId, p.id) },
                                onRepost = { nav.post(p.ownerId, p.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}
