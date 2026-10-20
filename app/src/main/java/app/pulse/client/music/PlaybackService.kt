package app.pulse.client.music

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.pulse.client.MainActivity
import app.pulse.client.R
import app.pulse.client.notifications.Notifier

/**
 * Background music playback. Media3 posts the media-style notification and handles
 * the foreground service lifecycle; audio focus and "becoming noisy" are handled by ExoPlayer.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(Notifier.CH_MEDIA)
                .setChannelName(R.string.channel_media)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Stop the service when the task is swiped away and nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        isRunning = false
        super.onDestroy()
    }

    companion object {
        /** Lets the UI reconnect the mini player after the activity was recreated. */
        @Volatile
        var isRunning = false
            private set
    }
}
