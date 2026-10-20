package app.pulse.client.core.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Small ExoPlayer wrapper for voice messages inside a chat. Must be used on the main thread. */
class VoicePlayer(private val context: Context, private val scope: CoroutineScope) {

    data class State(val key: String? = null, val progress: Float = 0f)

    private var player: ExoPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun toggle(key: String, url: String) {
        val p = player ?: ExoPlayer.Builder(context).build().also { created ->
            player = created
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) stopInternal()
                }
            })
        }
        if (_state.value.key == key) {
            if (p.isPlaying) p.pause() else p.play()
            return
        }
        p.setMediaItem(MediaItem.fromUri(url))
        p.prepare()
        p.play()
        _state.value = State(key, 0f)
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val d = p.duration
                if (d > 0) _state.value = State(key, (p.currentPosition.toFloat() / d).coerceIn(0f, 1f))
                delay(100)
            }
        }
    }

    private fun stopInternal() {
        ticker?.cancel()
        player?.stop()
        _state.value = State()
    }

    fun release() {
        ticker?.cancel()
        player?.release()
        player = null
        _state.value = State()
    }
}
