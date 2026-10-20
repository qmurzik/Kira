package app.pulse.client.ui.feed

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import app.pulse.client.data.api.userMessage
import app.pulse.client.data.repo.FeedKind
import app.pulse.client.domain.model.Post
import app.pulse.client.domain.model.StoryGroup
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Routes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(vm: FeedViewModel) {
    val nav = LocalNavigator.current
    val container = LocalAppContainer.current
    val session = LocalSession.current
    val accounts by container.accounts.accounts.collectAsStateWithLifecycle()
    val me = accounts.firstOrNull { it.userId == session.userId }
    val kind by vm.kind.collectAsStateWithLifecycle()
    val stories by vm.stories.collectAsStateWithLifecycle()
    val posts = (if (kind == FeedKind.News) vm.news else vm.recommended).collectAsLazyPagingItems()
    val snackbar = remember { SnackbarHostState() }
    var repostOf by remember { mutableStateOf<Post?>(null) }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val actions = remember(nav) {
        AttachmentActions(
            onPhoto = nav::photo,
            onVideo = nav::video,
            onWall = { o, p -> nav.post(o, p) },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { nav.profile(session.userId) }) {
                        Avatar(me?.photo, me?.name ?: "", 32.dp)
                    }
                    FeedKind.entries.forEach { k ->
                        Text(
                            if (k == FeedKind.News) "Новости" else "Рекомендации",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (k == kind) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (k == kind) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { vm.setKind(k) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { nav.open(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, "Поиск") }
                    IconButton(onClick = { nav.open(Routes.NOTIFICATIONS) }) { Icon(Icons.Outlined.Notifications, "Уведомления") }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = posts.loadState.refresh is LoadState.Loading && posts.itemCount > 0,
            onRefresh = {
                posts.refresh()
                vm.loadStories()
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (stories.isNotEmpty() && kind == FeedKind.News) {
                    item(key = "stories", contentType = "stories") {
                        StoriesRow(stories, onOpen = { nav.stories(it) })
                    }
                }
                if (kind == FeedKind.News) {
                    item(key = "composer", contentType = "composer") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().clickable { nav.open(Routes.CREATE_POST) },
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Avatar(me?.photo, me?.name ?: "", 36.dp)
                                Text(
                                    "Что у вас нового?",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                                )
                                Icon(Icons.Outlined.PhotoCamera, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                items(
                    count = posts.itemCount,
                    key = posts.itemKey { "p${it.position}" },
                    contentType = posts.itemContentType { "post" },
                ) { index ->
                    val item = posts[index] ?: return@items
                    PostCard(
                        post = item.post,
                        actions = actions,
                        onAuthor = nav::profile,
                        onOpen = { nav.post(item.post.ownerId, item.post.id) },
                        onLike = { vm.like(item.post) },
                        onRepost = { repostOf = item.post },
                    )
                }
                when (val append = posts.loadState.append) {
                    is LoadState.Loading -> item(key = "append", contentType = "loading") { LoadingRow() }
                    is LoadState.Error -> item(key = "append_error", contentType = "error") {
                        TextButton(onClick = posts::retry, modifier = Modifier.fillMaxWidth()) {
                            Text("Ошибка: ${append.error.userMessage()}. Повторить")
                        }
                    }
                    else -> Unit
                }
            }
            val refresh = posts.loadState.refresh
            if (posts.itemCount == 0) {
                when {
                    refresh is LoadState.Loading -> LoadingBox()
                    refresh is LoadState.Error -> ErrorBox(refresh.error.userMessage(), posts::retry)
                    else -> MessageBox("Лента пуста", "Подпишитесь на людей и сообщества")
                }
            }
        }
    }

    repostOf?.let { post ->
        var text by remember(post.key) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { repostOf = null },
            title = { Text("Поделиться на своей стене") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Ваш комментарий") })
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.repost(post, text)
                    repostOf = null
                }) { Text("Опубликовать") }
            },
            dismissButton = { TextButton(onClick = { repostOf = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun StoriesRow(groups: List<StoryGroup>, onOpen: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(groups, key = { _, g -> g.owner.id }) { index, g ->
                Column(
                    Modifier.widthIn(max = 72.dp).clickable { onOpen(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val ring = if (g.allSeen) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                    Avatar(
                        g.owner.photo,
                        g.owner.name,
                        60.dp,
                        modifier = Modifier.border(2.dp, ring, CircleShape).padding(3.dp),
                    )
                    Text(
                        g.owner.name.substringBefore(' '),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(68.dp).padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
