package app.pulse.client.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.Owner
import app.pulse.client.domain.model.PhotoItem
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.ListRow
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.MessageBox
import app.pulse.client.ui.navigation.LocalNavigator
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class GroupsViewModel(session: UserSession, private val userId: Long) : ViewModel() {
    private val repo = session.profiles
    val state = MutableStateFlow<ListState<Owner>>(ListState.Loading)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            state.value = ListState.Loading
            state.value = try {
                ListState.Ready(repo.groups(userId, 0, 1000).first)
            } catch (e: Exception) {
                ListState.Error(e.userMessage())
            }
        }
    }
}

@Composable
fun GroupsScreen(userId: Long) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "groups_$userId") { GroupsViewModel(session, userId) }
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Сообщества") }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ListState.Loading -> LoadingBox()
                is ListState.Error -> ErrorBox(s.message, vm::load)
                is ListState.Ready -> if (s.items.isEmpty()) {
                    MessageBox("Сообществ нет", "Или список скрыт настройками приватности")
                } else {
                    LazyColumn {
                        items(s.items, key = { it.id }, contentType = { "group" }) { g ->
                            ListRow(
                                title = g.name,
                                leading = { Avatar(g.photo, g.name, 48.dp) },
                                modifier = Modifier.clickable { nav.profile(g.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

class PhotosViewModel(session: UserSession, private val ownerId: Long) : ViewModel() {
    private val repo = session.profiles
    val photos = MutableStateFlow<List<PhotoItem>>(emptyList())
    val total = MutableStateFlow(-1)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        loadMore()
    }

    fun loadMore() {
        if (loading.value || (total.value >= 0 && photos.value.size >= total.value)) return
        viewModelScope.launch {
            loading.value = true
            try {
                val (list, count) = repo.photos(ownerId, photos.value.size)
                val known = photos.value.map { it.id }.toSet()
                photos.value = photos.value + list.filter { it.id !in known }
                total.value = if (list.isEmpty()) photos.value.size else count
                error.value = null
            } catch (e: Exception) {
                error.value = e.userMessage()
            } finally {
                loading.value = false
            }
        }
    }
}

@Composable
fun PhotosScreen(ownerId: Long) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "photos_$ownerId") { PhotosViewModel(session, ownerId) }
    val photos by vm.photos.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    Scaffold(topBar = { AppTopBar("Фотографии") }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                photos.isEmpty() && loading -> LoadingBox()
                photos.isEmpty() && error != null -> ErrorBox(error!!, vm::loadMore)
                photos.isEmpty() -> MessageBox("Фотографий нет")
                else -> {
                    val grid = rememberLazyGridState()
                    val nearEnd by remember {
                        derivedStateOf {
                            val info = grid.layoutInfo
                            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 12
                        }
                    }
                    LaunchedEffect(grid) {
                        snapshotFlow { nearEnd }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
                    }
                    LazyVerticalGrid(
                        state = grid,
                        columns = GridCells.Adaptive(110.dp),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(photos, key = { it.id }, contentType = { "photo" }) { p ->
                            AsyncImage(
                                model = p.thumb,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.aspectRatio(1f).background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { nav.photo(p.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Full-screen photo with pinch-to-zoom and double-tap to reset. */
@Composable
fun PhotoViewerScreen(url: String) {
    val nav = LocalNavigator.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    })
                }
                .transformable(transform)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        IconButton(
            onClick = { nav.back() },
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(4.dp),
        ) { Icon(Icons.Filled.Close, "Закрыть", tint = Color.White) }
    }
}
