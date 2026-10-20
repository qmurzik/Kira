package app.pulse.client.music

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Immutable
data class QueueEntry(val index: Int, val id: String, val title: String, val artist: String)

@Immutable
data class PlayerUi(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artwork: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
)

/**
 * App-wide connection to [PlaybackService] through a MediaController.
 * Connects lazily on first use; all calls must happen on the main thread.
 */
class MusicController(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null
    private var connecting = false
    private val pending = ArrayList<(MediaController) -> Unit>()
    private var ticker: Job? = null

    private val _ui = MutableStateFlow(PlayerUi())
    val ui: StateFlow<PlayerUi> = _ui.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    /** Runs [block] once connected (connecting on first use). */
    private fun withController(block: (MediaController) -> Unit) {
        controller?.let { block(it); return }
        pending += block
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            publish(c)
            startTicker()
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    /** Connect without playing (restores the mini player if the service is already running). */
    fun connect() = withController { }

    fun playTracks(tracks: List<Track>, startIndex: Int) {
        val items = tracks.map { it.toMediaItem() }
        withController { c ->
            c.setMediaItems(items, startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)), 0L)
            c.prepare()
            c.play()
        }
    }

    fun addToQueue(track: Track) = withController { it.addMediaItem(track.toMediaItem()) }

    fun toggle() = withController { if (it.isPlaying) it.pause() else it.play() }
    fun next() = withController { it.seekToNextMediaItem() }
    fun previous() = withController { it.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun jumpTo(index: Int) = withController { it.seekTo(index, 0L); it.play() }
    fun removeAt(index: Int) = withController { it.removeMediaItem(index) }
    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController {
        it.repeatMode = when (it.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() = withController {
        it.stop()
        it.clearMediaItems()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.isPlaying) _ui.value = _ui.value.copy(positionMs = c.currentPosition.coerceAtLeast(0))
                }
                delay(500)
            }
        }
    }

    private fun publish(p: Player) {
        val md = p.mediaMetadata
        val queue = (0 until p.mediaItemCount).map { i ->
            val item = p.getMediaItemAt(i)
            QueueEntry(i, item.mediaId, item.mediaMetadata.title?.toString().orEmpty(), item.mediaMetadata.artist?.toString().orEmpty())
        }
        _ui.value = PlayerUi(
            hasMedia = p.mediaItemCount > 0,
            title = md.title?.toString().orEmpty(),
            artist = md.artist?.toString().orEmpty(),
            artwork = md.artworkUri?.toString(),
            isPlaying = p.isPlaying,
            positionMs = p.currentPosition.coerceAtLeast(0),
            durationMs = p.duration.takeIf { it > 0 } ?: 0,
            shuffle = p.shuffleModeEnabled,
            repeatMode = p.repeatMode,
            queue = queue,
            currentIndex = p.currentMediaItemIndex,
        )
    }

    private fun Track.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artwork)
                .build()
        )
        .build()
}
