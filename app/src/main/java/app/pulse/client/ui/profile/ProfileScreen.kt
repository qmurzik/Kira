package app.pulse.client.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.PhotoItem
import app.pulse.client.domain.model.Post
import app.pulse.client.domain.model.Profile
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LoadingRow
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.feed.PostCard
import app.pulse.client.ui.navigation.LocalNavigator
import app.pulse.client.ui.navigation.Routes
import coil.compose.AsyncImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val session: UserSession, val id: Long) : ViewModel() {
    private val repo = session.profiles
    private val feed = session.feed

    val profile = MutableStateFlow<Profile?>(null)
    val photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val wall = MutableStateFlow<List<Post>>(emptyList())
    val wallTotal = MutableStateFlow(0)
    val wallLoading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)
    private val _events = Channel<String>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            error.value = null
            try {
                val p = repo.profile(id)
                profile.value = p
                if (p.canAccess) {
                    launch { photos.value = runCatching { repo.photos(id, 0, 12).first }.getOrDefault(emptyList()) }
                    loadWall(reset = true)
                }
            } catch (e: Exception) {
                error.value = e.userMessage()
            }
        }
    }

    fun loadWall(reset: Boolean = false) {
        if (wallLoading.value) return
        if (!reset && wall.value.size >= wallTotal.value && wallTotal.value > 0) return
        viewModelScope.launch {
            wallLoading.value = true
            runCatching { repo.wall(id, if (reset) 0 else wall.value.size) }
                .onSuccess { (list, total) ->
                    val known = if (reset) emptySet() else wall.value.map { it.key }.toSet()
                    wall.value = (if (reset) emptyList() else wall.value) + list.filter { it.key !in known }
                    wallTotal.value = if (list.isEmpty()) wall.value.size else total
                }
                .onFailure { _events.send(it.userMessage()) }
            wallLoading.value = false
        }
    }

    fun like(post: Post) {
        viewModelScope.launch {
            runCatching { feed.toggleLike(post) }
                .onSuccess { updated -> wall.value = wall.value.map { if (it.key == post.key) updated else it } }
                .onFailure { _events.send(it.userMessage()) }
        }
    }

    fun friendAction() {
        val p = profile.value ?: return
        viewModelScope.launch {
            try {
                when (p.friendStatus) {
                    0, 2 -> {
                        val r = repo.addFriend(id)
                        _events.send(if (r == 2) "Заявка принята" else "Заявка отправлена")
                    }
                    1, 3 -> {
                        repo.deleteFriend(id)
                        _events.send(if (p.friendStatus == 3) "Удалён из друзей" else "Заявка отменена")
                    }
                }
                profile.value = repo.profile(id)
            } catch (e: Exception) {
                _events.send(e.userMessage())
            }
        }
    }

    fun groupAction() {
        val p = profile.value ?: return
        viewModelScope.launch {
            try {
                if (p.isMember) repo.leaveGroup(-id) else repo.joinGroup(-id)
                profile.value = repo.profile(id)
            } catch (e: Exception) {
                _events.send(e.userMessage())
            }
        }
    }
}

@Composable
fun ProfileScreen(id: Long) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "profile_$id") { ProfileViewModel(session, id) }
    val profile by vm.profile.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val wall by vm.wall.collectAsStateWithLifecycle()
    val wallLoading by vm.wallLoading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val actions = remember(nav) {
        AttachmentActions(onPhoto = nav::photo, onVideo = nav::video, onWall = { o, p -> nav.post(o, p) })
    }
    val isMe = id == session.userId

    Scaffold(
        topBar = { AppTopBar(profile?.screenName?.let { "@$it" } ?: if (id < 0) "Сообщество" else "Профиль") },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val p = profile
        when {
            p == null && error != null -> ErrorBox(error!!, vm::load, Modifier.padding(padding))
            p == null -> LoadingBox(Modifier.padding(padding))
            else -> {
                val listState = rememberLazyListState()
                val nearEnd by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
                    }
                }
                LaunchedEffect(listState) {
                    snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadWall() }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header", contentType = "header") {
                        ProfileHeader(
                            p = p,
                            isMe = isMe,
                            onMessage = { nav.chat(id) },
                            onFriend = vm::friendAction,
                            onGroup = vm::groupAction,
                            onEdit = { nav.open(Routes.SETTINGS) },
                        )
                    }
                    item(key = "counters", contentType = "counters") {
                        Counters(p, id, onFriends = { nav.friends(id) }, onGroups = { nav.groups(id) }, onPhotos = { nav.photos(id) })
                    }
                    if (photos.isNotEmpty()) {
                        item(key = "photos", contentType = "photos") {
                            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
                                Column(Modifier.padding(vertical = 12.dp)) {
                                    Text(
                                        "Фотографии",
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.padding(horizontal = 12.dp).clickable { nav.photos(id) },
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        items(photos, key = { it.id }) { ph ->
                                            AsyncImage(
                                                model = ph.thumb,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp))
                                                    .clickable { nav.photo(ph.url) },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (!p.canAccess) {
                        item(key = "closed", contentType = "closed") {
                            MessageBox(
                                if (id < 0) "Это закрытое сообщество" else "Это закрытый профиль",
                                "Записи и фотографии видны только " + if (id < 0) "участникам" else "друзьям",
                            )
                        }
                    }
                    items(wall, key = { "w" + it.key }, contentType = { "post" }) { post ->
                        PostCard(
                            post = post,
                            actions = actions,
                            onAuthor = nav::profile,
                            onOpen = { nav.post(post.ownerId, post.id) },
                            onLike = { vm.like(post) },
                            onRepost = { nav.post(post.ownerId, post.id) },
                        )
                    }
                    if (wallLoading) item(key = "loading", contentType = "loading") { LoadingRow() }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    p: Profile,
    isMe: Boolean,
    onMessage: () -> Unit,
    onFriend: () -> Unit,
    onGroup: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(p.owner.photo, p.owner.name, 84.dp, online = p.owner.online)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.owner.name, style = MaterialTheme.typography.titleLarge)
                        if (p.owner.verified) {
                            Icon(
                                Icons.Filled.Verified,
                                "Подтверждён",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp).size(18.dp),
                            )
                        }
                    }
                    if (!p.status.isNullOrBlank()) {
                        Text(p.status, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
                    }
                    val info = listOfNotNull(
                        if (p.owner.isGroup) null else Format.lastSeen(p.owner.online, p.owner.lastSeen).ifEmpty { null },
                        p.city,
                        p.membersCount?.let { Format.plural(it, "участник", "участника", "участников") },
                    ).joinToString(" · ")
                    if (info.isNotEmpty()) {
                        Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            p.deactivated?.let {
                Text(
                    "Страница удалена или заблокирована",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (!p.description.isNullOrBlank()) {
                Text(p.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp), maxLines = 6)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isMe -> FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Настройки") }
                    p.owner.isGroup -> {
                        Button(onClick = onGroup, modifier = Modifier.weight(1f)) {
                            Text(if (p.isMember) "Вы участник" else "Вступить")
                        }
                        FilledTonalButton(onClick = onMessage, modifier = Modifier.weight(1f)) { Text("Сообщение") }
                    }
                    else -> {
                        if (p.canWrite) {
                            Button(onClick = onMessage, modifier = Modifier.weight(1f)) { Text("Сообщение") }
                        }
                        FilledTonalButton(onClick = onFriend, modifier = Modifier.weight(1f)) {
                            Text(
                                when (p.friendStatus) {
                                    1 -> "Заявка отправлена"
                                    2 -> "Принять заявку"
                                    3 -> "У вас в друзьях"
                                    else -> "Добавить в друзья"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Counters(p: Profile, id: Long, onFriends: () -> Unit, onGroups: () -> Unit, onPhotos: () -> Unit) {
    val entries = buildList<Triple<Int, String, () -> Unit>> {
        p.friends?.let { add(Triple(it, "друзей", onFriends)) }
        p.followers?.let { add(Triple(it, if (id < 0) "участников" else "подписчиков", {})) }
        p.photos?.let { add(Triple(it, "фото", onPhotos)) }
        p.videos?.let { add(Triple(it, "видео", {})) }
        p.groups?.let { add(Triple(it, "сообществ", onGroups)) }
    }
    if (entries.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            entries.forEach { (count, label, onClick) ->
                Column(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(Format.count(count), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
