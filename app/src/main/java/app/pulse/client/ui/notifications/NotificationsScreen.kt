package app.pulse.client.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.AppNotification
import app.pulse.client.domain.model.Owner
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.ListRow
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.components.SectionHeader
import app.pulse.client.ui.navigation.LocalNavigator
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class NotificationsViewModel(private val session: UserSession) : ViewModel() {
    private val repo = session.notifications
    val items = MutableStateFlow<List<AppNotification>>(emptyList())
    val requests = MutableStateFlow<List<Owner>>(emptyList())
    val loading = MutableStateFlow(false)
    val refreshing = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private var next: String? = null
    private var end = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            launch { requests.value = runCatching { session.profiles.requests() }.getOrDefault(emptyList()) }
            try {
                val page = repo.load(null)
                items.value = page.items
                next = page.nextFrom
                end = page.nextFrom == null
                repo.markViewed()
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                refreshing.value = false
            }
        }
    }

    fun loadMore() {
        if (loading.value || refreshing.value || end) return
        viewModelScope.launch {
            loading.value = true
            try {
                val page = repo.load(next)
                val known = items.value.map { it.key }.toSet()
                items.value = items.value + page.items.filter { it.key !in known }
                next = page.nextFrom
                end = page.nextFrom == null || page.items.isEmpty()
            } catch (e: Exception) {
                end = true
            } finally {
                loading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen() {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel { NotificationsViewModel(session) }
    val items by vm.items.collectAsStateWithLifecycle()
    val requests by vm.requests.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Уведомления") }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing && items.isNotEmpty(),
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val state = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
                }
            }
            LaunchedEffect(state) { snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadMore() } }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                if (requests.isNotEmpty()) {
                    item(key = "requests", contentType = "requests") {
                        ListRow(
                            title = "Заявки в друзья",
                            subtitle = requests.take(3).joinToString { it.name } +
                                if (requests.size > 3) " и ещё ${requests.size - 3}" else "",
                            leading = { Avatar(requests.first().photo, requests.first().name, 48.dp) },
                            modifier = Modifier.clickable { nav.friends(session.userId) },
                        )
                    }
                    item(key = "header", contentType = "header") { SectionHeader("События") }
                }
                items(items, key = { it.key }, contentType = { "notification" }) { n ->
                    NotificationRow(n, onActor = nav::profile)
                }
                if (loading) item(key = "loading", contentType = "loading") { LoadingRow() }
            }
            if (items.isEmpty()) {
                when {
                    refreshing -> LoadingBox()
                    error != null -> ErrorBox(error!!, vm::refresh)
                    requests.isEmpty() -> MessageBox("Уведомлений нет")
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(n: AppNotification, onActor: (Long) -> Unit) {
    val first = n.actors.firstOrNull()
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (n.unread) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface)
            .clickable(enabled = first != null) { first?.let { onActor(it.id) } }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            if (first != null) {
                Avatar(first.photo, first.name, 48.dp)
            } else {
                AsyncImage(
                    model = n.thumb,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            if (n.unread) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val who = when (n.actors.size) {
                0 -> ""
                1 -> n.actors[0].name + " "
                else -> n.actors[0].name + " и ещё ${n.actors.size - 1} "
            }
            Text(
                who + n.text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (n.unread) FontWeight.Medium else FontWeight.Normal,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            n.target?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Text(Format.postDate(n.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
