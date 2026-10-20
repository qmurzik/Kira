package app.pulse.client.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.VideoItem
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.navigation.LocalNavigator
import coil.compose.AsyncImage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

enum class VideoTab(val label: String) { Feed("Подписки"), Mine("Мои"), Search("Поиск") }

/** One paged list of videos (any source). */
class VideoListState {
    val items = MutableStateFlow<List<VideoItem>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    val end = MutableStateFlow(false)
    var cursor: String? = null
}

@OptIn(FlowPreview::class)
class VideoViewModel(private val session: UserSession) : ViewModel() {
    private val repo = session.videos
    val tab = MutableStateFlow(VideoTab.Feed)
    val query = MutableStateFlow("")
    val lists = VideoTab.entries.associateWith { VideoListState() }
    private val jobs = HashMap<VideoTab, Job>()

    init {
        load(VideoTab.Feed, reset = true)
        viewModelScope.launch {
            query.debounce(400).distinctUntilChanged().collect { load(VideoTab.Search, reset = true) }
        }
    }

    fun select(t: VideoTab) {
        tab.value = t
        val s = lists.getValue(t)
        if (s.items.value.isEmpty() && !s.loading.value && t != VideoTab.Search) load(t, reset = true)
    }

    fun loadMore() = load(tab.value, reset = false)

    fun load(t: VideoTab, reset: Boolean) {
        val s = lists.getValue(t)
        if (!reset && (s.loading.value || s.end.value)) return
        jobs[t]?.cancel()
        jobs[t] = viewModelScope.launch {
            if (reset) {
                s.cursor = null
                s.end.value = false
            }
            s.loading.value = true
            s.error.value = null
            try {
                val offset = if (reset) 0 else s.items.value.size
                val page: List<VideoItem> = when (t) {
                    VideoTab.Feed -> {
                        val (list, next) = repo.fromSubscriptions(if (reset) null else s.cursor)
                        s.cursor = next
                        if (next == null) s.end.value = true
                        list
                    }
                    VideoTab.Mine -> {
                        val (list, total) = repo.my(offset)
                        if (offset + list.size >= total || list.isEmpty()) s.end.value = true
                        list
                    }
                    VideoTab.Search -> {
                        val q = query.value.trim()
                        if (q.isEmpty()) {
                            s.end.value = true
                            emptyList()
                        } else {
                            repo.search(q, offset).also { if (it.isEmpty()) s.end.value = true }
                        }
                    }
                }
                val base = if (reset) emptyList() else s.items.value
                val known = base.map { it.apiId }.toSet()
                s.items.value = base + page.filter { it.apiId !in known }
            } catch (e: Exception) {
                s.error.value = e.userMessage()
            } finally {
                s.loading.value = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel { VideoViewModel(session) }
    val tab by vm.tab.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val state = vm.lists.getValue(tab)
    val items by state.items.collectAsStateWithLifecycle()
    val loading by state.loading.collectAsStateWithLifecycle()
    val error by state.error.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar("Видео", showBack = false) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                VideoTab.entries.forEach { t -> Tab(selected = t == tab, onClick = { vm.select(t) }, text = { Text(t.label) }) }
            }
            if (tab == VideoTab.Search) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Поиск видео") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                )
            }
            Box(Modifier.fillMaxSize()) {
                val listState = rememberLazyListState()
                val nearEnd by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                    }
                }
                LaunchedEffect(listState, tab) {
                    snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (tab == VideoTab.Feed) {
                        item(key = "catalog_note", contentType = "note") { CatalogNote() }
                    }
                    items(items, key = { it.apiId }, contentType = { "video" }) { v ->
                        VideoRow(v, onClick = { nav.video(v.apiId) })
                    }
                    if (loading && items.isNotEmpty()) item(key = "loading", contentType = "loading") { LoadingRow() }
                }
                when {
                    items.isNotEmpty() -> Unit
                    loading -> LoadingBox()
                    error != null -> ErrorBox(error!!, { vm.load(tab, reset = true) })
                    tab == VideoTab.Search && query.isBlank() -> MessageBox("Введите запрос")
                    else -> MessageBox("Видео нет")
                }
            }
        }
    }
}

@Composable
private fun CatalogNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Каталог VK Видео (подборки, тренды) не предоставляется публичным API. " +
                    "Здесь — видео из ваших подписок.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
fun VideoRow(v: VideoItem, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.width(160.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
            AsyncImage(model = v.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text(
                if (v.isLive) "LIVE" else Format.duration(v.duration),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                    .background(if (v.isLive) MaterialTheme.colorScheme.error else Color(0x99000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    if (v.views > 0) Format.plural(v.views, "просмотр", "просмотра", "просмотров") else null,
                    Format.shortDate(v.date).ifEmpty { null },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
