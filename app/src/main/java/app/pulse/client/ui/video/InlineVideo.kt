package app.pulse.client.ui.video

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Lightweight autoplaying video without controls (stories, clips).
 * The player is created per URL and released when the composable leaves the screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun InlineVideo(
    url: String,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    loop: Boolean = false,
    muted: Boolean = false,
    fill: Boolean = false,
    onEnded: () -> Unit = {},
    onProgress: (Float) -> Unit = {},
) {
    val context = LocalContext.current
    val ended by rememberUpdatedState(onEnded)
    val progress by rememberUpdatedState(onProgress)
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(player, playing) {
        player.playWhenReady = playing
        while (isActive && playing) {
            val d = player.duration
            if (d > 0) progress((player.currentPosition.toFloat() / d).coerceIn(0f, 1f))
            delay(100)
        }
    }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = if (fill) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                this.player = player
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
    )
}
