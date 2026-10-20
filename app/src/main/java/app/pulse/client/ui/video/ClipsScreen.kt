package app.pulse.client.ui.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Comment
import app.pulse.client.domain.model.VideoItem
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.components.UnavailableBox
import app.pulse.client.ui.theme.AppTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * VK Clips have no public API (no documented methods for the clips feed).
 * This screen is an honest substitute: a clips-style vertical feed of the user's own
 * vertical videos (video.get), with autoplay, preloading of the neighbour page, likes and comments.
 */
class ClipsViewModel(private val session: UserSession) : ViewModel() {
    private val repo = session.videos
    val clips = MutableStateFlow<List<VideoItem>>(emptyList())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                // Files (direct URLs) only come with video.get by id, so resolve the vertical ones.
                val vertical = ArrayList<VideoItem>()
                var offset = 0
                while (offset < 200 && vertical.size < 30) {
                    val (page, total) = repo.my(offset, 50)
                    vertical += page.filter { it.isVertical && it.duration in 1..180 }
                    offset += 50
                    if (offset >= total || page.isEmpty()) break
                }
                val resolved = repo.getMany(vertical.take(30).map { it.apiId })
                clips.value = resolved.filter { it.files.isNotEmpty() }
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                loading.value = false
            }
        }
    }

    fun like(v: VideoItem) {
        viewModelScope.launch {
            runCatching { repo.toggleLike(v) }.onSuccess { updated ->
                clips.value = clips.value.map { if (it.apiId == v.apiId) updated else it }
            }
        }
    }
}

@Composable
fun ClipsScreen() {
    val session = LocalSession.current
    val container = LocalAppContainer.current
    val vm = viewModel { ClipsViewModel(session) }
    val clips by vm.clips.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    var commentsFor by remember { mutableStateOf<VideoItem?>(null) }
    var resumed by remember { mutableStateOf(true) }
    LifecycleResumeEffect(Unit) {
        resumed = true
        onPauseOrDispose { resumed = false }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            loading -> LoadingBox()
            error != null -> ErrorBox(error!!, vm::load)
            clips.isEmpty() -> UnavailableBox(
                "Лента клипов",
                "У VK нет публичных методов для клипов. Здесь показываются ваши собственные вертикальные видео, " +
                    "но среди них не нашлось ни одного, доступного для воспроизведения.",
                Modifier.background(MaterialTheme.colorScheme.background),
            )
            else -> {
                val pager = rememberPagerState { clips.size }
                VerticalPager(
                    state = pager,
                    beyondViewportPageCount = 1, // composes (and so buffers) the next clip in advance
                    key = { clips[it].apiId },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val clip = clips[page]
                    val url = clip.files["Авто (HLS)"] ?: clip.files.values.first()
                    val autoplay = settings?.autoplayVideo != false
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(model = clip.thumb, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        InlineVideo(
                            url = url,
                            playing = resumed && autoplay && pager.currentPage == page,
                            loop = true,
                            fill = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ClipOverlay(
                            clip,
                            onLike = { vm.like(clip) },
                            onComments = { commentsFor = clip },
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
                Row(
                    Modifier.statusBarsPadding().padding(12.dp).background(Color(0x66000000), MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Info, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Text(
                        " Ваши вертикальные видео · клипы VK недоступны через API",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
    commentsFor?.let { VideoCommentsSheet(it, onDismiss = { commentsFor = null }) }
}

@Composable
private fun ClipOverlay(clip: VideoItem, onLike: () -> Unit, onComments: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(clip.title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!clip.description.isNullOrBlank()) {
                Text(clip.description, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall, maxLines = 3)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onLike)) {
                Icon(
                    if (clip.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    "Нравится",
                    tint = if (clip.liked) AppTheme.extra.like else Color.White,
                    modifier = Modifier.size(32.dp),
                )
                Text(Format.count(clip.likes), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onComments)) {
                Icon(Icons.Outlined.ChatBubbleOutline, "Комментарии", tint = Color.White, modifier = Modifier.size(30.dp))
                Text(Format.count(clip.comments), color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

class VideoCommentsViewModel(session: UserSession, private val video: VideoItem) : ViewModel() {
    private val repo = session.videos
    val comments = MutableStateFlow<List<Comment>>(emptyList())
    val loading = MutableStateFlow(true)
    val error = MutableStateFlow<String?>(null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            try {
                comments.value = repo.comments(video, 0).first
                error.value = null
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                loading.value = false
            }
        }
    }

    fun send(text: String) {
        viewModelScope.launch {
            try {
                repo.addComment(video, text)
                load()
            } catch (e: Exception) {
                error.value = e.userMessage()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoCommentsSheet(video: VideoItem, onDismiss: () -> Unit) {
    val session = LocalSession.current
    val vm = viewModel(key = "vcomments_${video.apiId}") { VideoCommentsViewModel(session, video) }
    val comments by vm.comments.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().heightIn(min = 240.dp, max = 560.dp).imePadding().navigationBarsPadding()) {
            Text("Комментарии", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            Box(Modifier.weight(1f, fill = false)) {
                when {
                    loading && comments.isEmpty() -> LoadingRow()
                    error != null && comments.isEmpty() -> MessageBox("Комментарии недоступны", error)
                    comments.isEmpty() -> MessageBox("Комментариев пока нет")
                    else -> LazyColumn {
                        items(comments, key = { it.id }) { c ->
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Avatar(c.author.photo, c.author.name, 32.dp)
                                Column(Modifier.padding(start = 10.dp)) {
                                    Text(c.author.name, style = MaterialTheme.typography.labelLarge)
                                    Text(c.text, style = MaterialTheme.typography.bodyMedium)
                                    Text(Format.postDate(c.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Комментарий") },
                    modifier = Modifier.weight(1f).padding(8.dp),
                )
                IconButton(
                    onClick = {
                        vm.send(input.trim())
                        input = ""
                    },
                    enabled = input.isNotBlank(),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить") }
            }
        }
    }
}
