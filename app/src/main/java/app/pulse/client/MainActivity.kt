package app.pulse.client

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import app.pulse.client.notifications.Notifier
import app.pulse.client.ui.AppRoot

/** Single activity. FragmentActivity is required by BiometricPrompt (app lock). */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as PulseApp).container
        handleIntent(intent)
        setContent { AppRoot(container) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
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
