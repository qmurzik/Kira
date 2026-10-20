package app.pulse.client.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Comment
import app.pulse.client.domain.model.Post
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Attachments
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.theme.AppTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class PostViewModel(session: UserSession, val ownerId: Long, val postId: Long) : ViewModel() {
    private val repo = session.feed

    val post = MutableStateFlow<Post?>(null)
    val comments = MutableStateFlow<List<Comment>>(emptyList())
    val total = MutableStateFlow(0)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            loading.value = true
            error.value = null
            try {
                post.value = repo.getPost(ownerId, postId)
                val (list, count) = repo.comments(ownerId, postId, 0)
                comments.value = list
                total.value = count
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                loading.value = false
            }
        }
    }

    fun loadMore() {
        if (loading.value || comments.value.size >= total.value) return
        viewModelScope.launch {
            loading.value = true
            runCatching { repo.comments(ownerId, postId, comments.value.size) }
                .onSuccess { (list, count) ->
                    val known = comments.value.map { it.id }.toSet()
                    comments.value = comments.value + list.filter { it.id !in known }
                    total.value = count
                    if (list.isEmpty()) total.value = comments.value.size
                }
                .onFailure { _events.send(it.userMessage()) }
            loading.value = false
        }
    }

    fun likePost() {
        val p = post.value ?: return
        viewModelScope.launch {
            runCatching { repo.toggleLike(p) }.onSuccess { post.value = it }.onFailure { _events.send(it.userMessage()) }
        }
    }

    fun likeComment(c: Comment) {
        viewModelScope.launch {
            runCatching { repo.likeComment(ownerId, c) }
                .onSuccess { updated -> comments.value = comments.value.map { if (it.id == c.id) updated else it } }
                .onFailure { _events.send(it.userMessage()) }
        }
    }

    fun send(text: String, replyTo: Comment?) {
        viewModelScope.launch {
            try {
                repo.addComment(ownerId, postId, text, replyTo?.id)
                val (list, count) = repo.comments(ownerId, postId, 0)
                comments.value = list
                total.value = count
            } catch (e: Exception) {
                _events.send(e.userMessage())
            }
        }
    }
}

@Composable
fun PostScreen(ownerId: Long, postId: Long) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "post_${ownerId}_$postId") { PostViewModel(session, ownerId, postId) }
    val post by vm.post.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var input by rememberSaveable { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<Comment?>(null) }

    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val actions = remember(nav) {
        AttachmentActions(onPhoto = nav::photo, onVideo = nav::video, onWall = { o, p -> nav.post(o, p) })
    }

    Scaffold(
        topBar = { AppTopBar("Запись") },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            val p = post
            when {
                p == null && loading -> LoadingBox(Modifier.weight(1f))
                p == null -> ErrorBox(error ?: "Запись недоступна", vm::load, Modifier.weight(1f))
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                    item(key = "post", contentType = "post") {
                        PostCard(
                            post = p,
                            actions = actions,
                            onAuthor = nav::profile,
                            onOpen = null,
                            onLike = vm::likePost,
                            onRepost = {},
                            expandedByDefault = true,
                        )
                    }
                    item(key = "header", contentType = "header") {
                        Text(
                            if (total > 0) Format.plural(total, "комментарий", "комментария", "комментариев") else "Комментариев пока нет",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(comments, key = { it.id }, contentType = { "comment" }) { c ->
                        CommentItem(
                            c,
                            actions,
                            onAuthor = nav::profile,
                            onReply = { replyTo = it },
                            onLike = vm::likeComment,
                        )
                    }
                    if (comments.size < total) {
                        item(key = "more", contentType = "more") {
                            if (loading) {
                                LoadingRow()
                            } else {
                                Text(
                                    "Показать ещё",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth().clickable(onClick = vm::loadMore).padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (p != null && p.canComment) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        replyTo?.let { r ->
                            Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Ответ ${r.author.name}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { replyTo = null }) { Icon(Icons.Filled.Close, "Отменить") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = input,
                                onValueChange = { input = it },
                                placeholder = { Text("Комментарий") },
                                maxLines = 5,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    vm.send(input.trim(), replyTo)
                                    input = ""
                                    replyTo = null
                                },
                                enabled = input.isNotBlank(),
                            ) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentItem(
    c: Comment,
    actions: AttachmentActions,
    onAuthor: (Long) -> Unit,
    onReply: (Comment) -> Unit,
    onLike: (Comment) -> Unit,
    nested: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(start = if (nested) 56.dp else 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
        Avatar(c.author.photo, c.author.name, if (nested) 28.dp else 36.dp, modifier = Modifier.clickable { onAuthor(c.author.id) })
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(c.author.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            val prefix = c.replyToName?.let { "$it, " }.orEmpty()
            if (c.text.isNotEmpty()) Text(prefix + c.text, style = MaterialTheme.typography.bodyMedium)
            Attachments(c.attachments, actions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.postDate(c.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Ответить",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp).clickable { onReply(c) },
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onLike(c) }) {
            Icon(
                if (c.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = "Нравится",
                tint = if (c.liked) AppTheme.extra.like else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            if (c.likes > 0) Text(Format.count(c.likes), style = MaterialTheme.typography.labelSmall)
        }
    }
    if (!nested) {
        c.thread.forEach { t -> CommentItem(t, actions, onAuthor, onReply, onLike, nested = true) }
        if (c.threadCount > c.thread.size) {
            Text(
                "Ещё ответов: ${c.threadCount - c.thread.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp, bottom = 6.dp),
            )
        }
    }
}
