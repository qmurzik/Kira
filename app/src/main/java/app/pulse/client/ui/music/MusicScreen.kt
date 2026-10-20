package app.pulse.client.ui.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import app.pulse.client.core.util.Format
import app.pulse.client.music.LocalAudioRepository
import app.pulse.client.music.MusicController
import app.pulse.client.music.PlayerUi
import app.pulse.client.music.Track
import app.pulse.client.ui.LocalAppContainer
import app.pulse.client.ui.components.AppTopBar
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.MessageBox
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

private val audioPermission =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

class MusicViewModel(private val repo: LocalAudioRepository) : ViewModel() {
    val tracks = MutableStateFlow<List<Track>?>(null)
    val error = MutableStateFlow<String?>(null)

    fun load() {
        viewModelScope.launch {
            tracks.value = try {
                repo.tracks()
            } catch (e: Exception) {
                error.value = e.message
                emptyList()
            }
        }
    }
}

private enum class MusicTab(val label: String) { Tracks("Треки"), Albums("Альбомы"), Artists("Исполнители") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val music = container.music
    val vm = viewModel { MusicViewModel(container.localAudio) }
    val tracks by vm.tracks.collectAsStateWithLifecycle()
    val ui by music.ui.collectAsStateWithLifecycle()
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    LaunchedEffect(granted) { if (granted) vm.load() }
    LaunchedEffect(Unit) { music.connect() }

    Scaffold(topBar = { AppTopBar(group ?: "Музыка", onBack = group?.let { { group = null } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            VkAudioNote()
            if (!granted) {
                MessageBox(
                    "Нужен доступ к музыке на устройстве",
                    "Приложение воспроизводит ваши локальные аудиофайлы.",
                    Icons.Outlined.MusicNote,
                    "Разрешить",
                    { request.launch(audioPermission) },
                )
            } else if (tracks == null) {
                LoadingBox()
            } else {
            val all = tracks.orEmpty()
            if (group == null) {
                PrimaryTabRow(selectedTabIndex = tab) {
                    MusicTab.entries.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t.label) }) }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск по музыке на устройстве") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
            val filtered = all.filter {
                query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
            }
            Box(Modifier.weight(1f)) {
                when {
                    all.isEmpty() -> MessageBox("Музыки на устройстве не найдено")
                    group != null -> {
                        val inGroup = filtered.filter { it.album == group || it.artist == group }
                        TrackList(inGroup, ui) { i -> music.playTracks(inGroup, i) }
                    }
                    tab == MusicTab.Tracks.ordinal -> TrackList(filtered, ui) { i -> music.playTracks(filtered, i) }
                    else -> {
                        val groups = filtered.groupBy { if (tab == MusicTab.Albums.ordinal) it.album else it.artist }
                            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
                        LazyColumn {
                            items(groups.entries.toList(), key = { it.key }) { (name, list) ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { group = name }.padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = list.first().artwork,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    )
                                    Column(Modifier.padding(start = 12.dp)) {
                                        Text(name.ifEmpty { "Без названия" }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                        Text(
                                            Format.plural(list.size, "трек", "трека", "треков"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
            if (ui.hasMedia) MiniPlayer(ui, music, onOpen = { showPlayer = true })
        }
    }
    if (showPlayer) {
        ModalBottomSheet(onDismissRequest = { showPlayer = false }) { FullPlayer(ui, music) }
    }
}

@Composable
private fun VkAudioNote() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Аудиозаписи, плейлисты и поиск музыки VK недоступны через API: методы audio.* закрыты для сторонних " +
                    "приложений. Здесь — музыка с вашего устройства.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun TrackList(tracks: List<Track>, ui: PlayerUi, onPlay: (Int) -> Unit) {
    LazyColumn {
        itemsIndexed(tracks, key = { _, t -> t.id }, contentType = { _, _ -> "track" }) { i, t ->
            val current = ui.queue.getOrNull(ui.currentIndex)?.id == t.id.toString()
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(i) }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = t.artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        t.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(t.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(Format.duration((t.durationMs / 1000).toInt()), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** Compact player bar; also shown above the bottom navigation on other screens. */
@Composable
fun MiniPlayer(ui: PlayerUi, music: MusicController, onOpen: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = ui.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(ui.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ui.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = music::toggle) {
                Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ui.isPlaying) "Пауза" else "Играть")
            }
            IconButton(onClick = music::next) { Icon(Icons.Filled.SkipNext, "Следующий") }
            IconButton(onClick = music::stop) { Icon(Icons.Outlined.Close, "Остановить") }
        }
    }
}

@Composable
fun FullPlayer(ui: PlayerUi, music: MusicController) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var showQueue by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showQueue) {
            Text("Очередь", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().aspectRatio(0.9f)) {
                items(ui.queue, key = { "${it.index}_${it.id}" }) { q ->
                    Row(
                        Modifier.fillMaxWidth().clickable { music.jumpTo(q.index) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                q.title,
                                color = if (q.index == ui.currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(q.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        if (q.index != ui.currentIndex) {
                            IconButton(onClick = { music.removeAt(q.index) }) { Icon(Icons.Outlined.Close, "Убрать из очереди") }
                        }
                    }
                }
            }
        } else {
            AsyncImage(
                model = ui.artwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Spacer(Modifier.size(16.dp))
            Text(ui.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(ui.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val duration = ui.durationMs.coerceAtLeast(1)
        Slider(
            value = if (dragging) dragValue else (ui.positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                music.seekTo((dragValue * duration).toLong())
                dragging = false
            },
            modifier = Modifier.padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth()) {
            Text(Format.duration((ui.positionMs / 1000).toInt()), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(Format.duration((ui.durationMs / 1000).toInt()), style = MaterialTheme.typography.labelSmall)
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = music::toggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    "Перемешать",
                    tint = if (ui.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = music::previous) { Icon(Icons.Filled.SkipPrevious, "Предыдущий", Modifier.size(32.dp)) }
            FilledIconButton(onClick = music::toggle, modifier = Modifier.size(64.dp)) {
                Icon(if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (ui.isPlaying) "Пауза" else "Играть", Modifier.size(36.dp))
            }
            IconButton(onClick = music::next) { Icon(Icons.Filled.SkipNext, "Следующий", Modifier.size(32.dp)) }
            IconButton(onClick = music::cycleRepeat) {
                Icon(
                    if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Повтор",
                    tint = if (ui.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(Modifier.clickable { showQueue = !showQueue }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.QueueMusic, null)
            Spacer(Modifier.width(8.dp))
            Text(if (showQueue) "Сейчас играет" else "Очередь (${ui.queue.size})")
        }
    }
}
