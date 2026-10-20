package app.pulse.client.ui.video

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.Rational
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.pulse.client.MainActivity
import app.pulse.client.core.util.Format
import app.pulse.client.data.api.userMessage
import app.pulse.client.di.UserSession
import app.pulse.client.domain.model.VideoItem
import app.pulse.client.ui.LocalSession
import app.pulse.client.ui.components.ErrorBox
import app.pulse.client.ui.components.LoadingBox
import app.pulse.client.ui.components.LocalDataSaver
import app.pulse.client.ui.components.openUrl
import app.pulse.client.ui.navigation.LocalNavigator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class VideoPlayerViewModel(session: UserSession, private val apiId: String) : ViewModel() {
    private val repo = session.videos
    val video = MutableStateFlow<VideoItem?>(null)
    val error = MutableStateFlow<String?>(null)

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            error.value = null
            try {
                video.value = repo.get(apiId)
            } catch (e: Exception) {
                error.value = e.userMessage()
            }
        }
    }

    fun like() {
        val v = video.value ?: return
        viewModelScope.launch { runCatching { repo.toggleLike(v) }.onSuccess { video.value = it } }
    }
}

@Composable
fun VideoPlayerScreen(apiId: String) {
    val session = LocalSession.current
    val nav = LocalNavigator.current
    val vm = viewModel(key = "video_$apiId") { VideoPlayerViewModel(session, apiId) }
    val video by vm.video.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? MainActivity
    val inPip by (activity?.inPictureInPicture ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }

    // Fullscreen: landscape + hidden system bars, restored when leaving the screen.
    DisposableEffect(fullscreen, activity) {
        val window = activity?.window
        if (activity != null && window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            if (activity != null && window != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val v = video
    Box(Modifier.fillMaxSize().background(if (fullscreen || inPip) Color.Black else MaterialTheme.colorScheme.background)) {
        when {
            v == null && error != null -> ErrorBox(error!!, vm::load)
            v == null -> LoadingBox()
            else -> {
                val playerModifier = if (fullscreen || inPip) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier.fillMaxWidth().aspectRatio(
                        if (v.width > 0 && v.height > 0) (v.width.toFloat() / v.height).coerceIn(0.56f, 2.4f) else 16f / 9f
                    )
                }
                Column(Modifier.fillMaxSize()) {
                    if (!fullscreen && !inPip) {
                        Row(Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                            Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                    when {
                        v.files.isNotEmpty() -> NativePlayer(
                            v = v,
                            modifier = playerModifier,
                            controls = !inPip,
                            fullscreen = fullscreen,
                            onToggleFullscreen = { fullscreen = !fullscreen },
                            onPip = { activity?.enterPip(v.width, v.height) },
                            activity = activity,
                        )
                        v.playerUrl != null -> EmbedPlayer(v.playerUrl, playerModifier)
                        else -> Box(playerModifier.background(Color.Black), contentAlignment = Alignment.Center) {
                            Text("Видео нельзя воспроизвести в приложении", color = Color.White)
                        }
                    }
                    if (!fullscreen && !inPip) {
                        VideoInfo(
                            v,
                            onLike = vm::like,
                            onComments = { showComments = true },
                        )
                    }
                }
            }
        }
    }
    if (showComments && v != null) {
        VideoCommentsSheet(v, onDismiss = { showComments = false })
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun NativePlayer(
    v: VideoItem,
    modifier: Modifier,
    controls: Boolean,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    onPip: () -> Unit,
    activity: MainActivity?,
) {
    val context = LocalContext.current
    val labels = v.files.keys.toList()
    // Default: HLS (adaptive) if present, otherwise 720p or the best below it.
    val saver = LocalDataSaver.current
    var quality by rememberSaveable(v.apiId) {
        mutableStateOf(
            if (saver) {
                labels.firstOrNull { it == "360p" } ?: labels.last()
            } else {
                labels.firstOrNull { it.startsWith("Авто") } ?: labels.firstOrNull { it == "720p" } ?: labels.first()
            }
        )
    }
    var menu by remember { mutableStateOf(false) }
    val player = remember(v.apiId) { ExoPlayer.Builder(context).build() }
    DisposableEffect(player) {
        activity?.pipEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        activity?.pipAspect = if (v.width > 0 && v.height > 0) Rational(v.width, v.height) else Rational(16, 9)
        onDispose {
            activity?.pipEnabled = false
            player.release()
        }
    }
    LaunchedEffect(player, quality) {
        val url = v.files[quality] ?: return@LaunchedEffect
        val position = player.currentPosition
        val item = MediaItem.Builder().setUri(url).apply {
            if (quality.startsWith("Авто")) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player.setMediaItem(item)
        player.prepare()
        if (position > 0) player.seekTo(position)
        player.playWhenReady = true
    }
    Box(modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PlayerView(ctx).apply { this.player = player } },
            update = { view -> view.useController = controls },
        )
        if (controls) {
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.Settings, "Качество", tint = Color.White) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        labels.forEach { l ->
                            DropdownMenuItem(
                                text = { Text(if (l == quality) "✓ $l" else l) },
                                onClick = {
                                    quality = l
                                    menu = false
                                },
                            )
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    IconButton(onClick = onPip) { Icon(Icons.Outlined.PictureInPictureAlt, "Картинка в картинке", tint = Color.White) }
                }
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                        if (fullscreen) "Выйти из полноэкранного режима" else "Во весь экран",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

/** Fallback when the API gives only an embed player URL (no direct files for third-party apps). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedPlayer(url: String, modifier: Modifier) {
    AndroidView(
        modifier = modifier.background(Color.Black),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.allowFileAccess = false
                webChromeClient = WebChromeClient()
                loadUrl(url)
            }
        },
        onRelease = { it.destroy() },
    )
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoInfo(v: VideoItem, onLike: () -> Unit, onComments: () -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(v.title, style = MaterialTheme.typography.titleMedium)
        Text(
            listOfNotNull(
                if (v.views > 0) Format.plural(v.views, "просмотр", "просмотра", "просмотров") else null,
                Format.postDate(v.date).ifEmpty { null },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = onLike,
                label = { Text(Format.count(v.likes)) },
                leadingIcon = { Icon(if (v.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, "Нравится") },
            )
            AssistChip(
                onClick = onComments,
                label = { Text(if (v.comments > 0) Format.count(v.comments) else "Комментарии") },
                leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
            )
            v.external?.let { ext ->
                AssistChip(
                    onClick = { openUrl(context, ext) },
                    label = { Text("Открыть источник") },
                    leadingIcon = { Icon(Icons.Outlined.OpenInNew, null) },
                )
            }
        }
        if (v.files.isEmpty() && v.playerUrl != null) {
            Text(
                "API не отдал прямые файлы этого видео, поэтому используется встроенный плеер VK (выбор качества и PiP в нём недоступны).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!v.description.isNullOrBlank()) {
            Text(v.description, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
