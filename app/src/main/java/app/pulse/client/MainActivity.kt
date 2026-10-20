package app.pulse.client

import android.app.PictureInPictureParams
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.pulse.client.notifications.Notifier
import app.pulse.client.ui.AppRoot
import app.pulse.client.ui.settings.LockGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single activity. FragmentActivity is required by BiometricPrompt (app lock). */
class MainActivity : FragmentActivity() {

    private val _pip = MutableStateFlow(false)
    val inPictureInPicture: StateFlow<Boolean> = _pip.asStateFlow()

    /** Set by the video player while a native video is on screen. */
    var pipEnabled = false
    var pipAspect = Rational(16, 9)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PulseApp).container
        addOnPictureInPictureModeChangedListener { info -> _pip.value = info.isInPictureInPictureMode }
        handleIntent(intent)
        setContent {
            AppRoot(container) { settings, content -> LockGate(settings, content) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Leaving the app while a video plays switches to picture-in-picture (API 26+). */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (pipEnabled) enterPip(pipAspect.numerator, pipAspect.denominator)
    }

    fun enterPip(width: Int, height: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Android limits PiP aspect ratio to 1:2.39 .. 2.39:1.
        val ratio = if (width > 0 && height > 0) {
            val r = width.toFloat() / height
            when {
                r > 2.39f -> Rational(239, 100)
                r < 1 / 2.39f -> Rational(100, 239)
                else -> Rational(width, height)
            }
        } else {
            Rational(16, 9)
        }
        runCatching {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(ratio).build())
        }
    }

    private fun handleIntent(intent: Intent?) {
        val peer = intent?.getLongExtra(Notifier.EXTRA_OPEN_PEER, 0L) ?: 0L
        val user = intent?.getLongExtra(Notifier.EXTRA_USER, 0L) ?: 0L
        if (peer != 0L && user != 0L) {
            val container = (application as PulseApp).container
            container.pendingChat.value = user to peer
            intent?.removeExtra(Notifier.EXTRA_OPEN_PEER)
        }
    }
}
