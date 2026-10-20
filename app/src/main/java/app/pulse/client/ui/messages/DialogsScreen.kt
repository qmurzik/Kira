package app.pulse.client.ui.messages

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.domain.model.Conversation
import app.pulse.client.notifications.MessagesSyncWorker
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.CounterBadge
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.theme.AppTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Dialog list. With [onPick] set it works as the "forward to…" picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogsScreen(onPick: ((Long) -> Unit)? = null) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel { DialogsViewModel(session) }
    val items by vm.items.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val typing by vm.typing.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.searchResults.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }

    // Android 13+: ask once for notification permission when the messenger is opened.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (onPick == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val open: (Conversation) -> Unit = { c -> if (onPick != null) onPick(c.peerId) else nav.chat(c.peerId) }

    Scaffold(
        topBar = {
            if (searching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = query,
                            onValueChange = vm::setQuery,
                            placeholder = { Text("Поиск по диалогам") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { searching = false; vm.setQuery("") }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Закрыть поиск")
                        }
                    },
                    actions = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, "Очистить") }
                        }
                    },
                )
            } else {
                AppTopBar(
                    title = if (onPick != null) "Переслать в…" else "Мессенджер",
                    showBack = onPick != null,
                    actions = {
                        IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Поиск") }
                    },
                )
            }
        },
    ) { padding ->
        val list = if (searching && query.isNotBlank()) results.orEmpty() else items
        PullToRefreshBox(
            isRefreshing = meta.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val state = rememberLazyListState()
            val nearEnd by remember {
                derivedStateOf {
                    val info = state.layoutInfo
                    info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
                }
            }
            LaunchedEffect(state) {
                snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
            }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                if (!searching) {
                    item(key = "filters", contentType = "filters") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(DialogFilter.entries, key = { it.name }) { f ->
                                FilterChip(selected = f == filter, onClick = { vm.setFilter(f) }, label = { Text(f.label) })
                            }
                        }
                    }
                }
                items(list, key = { it.peerId }, contentType = { "dialog" }) { c ->
                    DialogRow(c, typing = typing[c.peerId], onClick = { open(c) })
                }
                if (meta.loading) item(key = "loading", contentType = "loading") { LoadingRow() }
            }
            when {
                list.isEmpty() && meta.error != null -> ErrorBox(meta.error!!, vm::refresh)
                list.isEmpty() && !meta.refreshing && searching && query.isNotBlank() ->
                    MessageBox("Ничего не найдено")
                list.isEmpty() && !meta.refreshing && !searching ->
                    MessageBox("Диалогов нет", if (filter == DialogFilter.All) null else "Попробуйте другой фильтр")
            }
        }
    }
}

@Composable
private fun DialogRow(c: Conversation, typing: List<Long>?, onClick: () -> Unit) {
    val last = c.lastMessage
    val unread = c.unreadCount > 0 || c.markedUnread
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.photo, c.title, 56.dp, online = c.online)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (c.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Закреплён",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    Format.shortDate(last?.date ?: 0),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!typing.isNullOrEmpty()) {
                    Text(
                        if (c.isChat) "печатают…" else "печатает…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val prefix = c.lastSenderName?.let { "$it: " }.orEmpty()
                    Text(
                        prefix + (last?.let { MessagesSyncWorker.preview(it) } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                when {
                    unread -> CounterBadge(c.unreadCount.coerceAtLeast(1), modifier = Modifier.padding(start = 8.dp))
                    last != null && last.out && c.outRead < last.id -> Box(
                        Modifier.padding(start = 8.dp).size(8.dp).clip(CircleShape).background(AppTheme.extra.unreadBadge)
                    )
                }
            }
        }
    }
}
