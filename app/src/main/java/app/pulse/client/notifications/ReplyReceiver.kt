package app.pulse.client.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.pulse.client.PulseApp
import app.pulse.client.data.api.userMessage
import kotlinx.coroutines.launch

/** Handles "Reply" and "Mark as read" from message notifications. */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PulseApp).container
        val userId = intent.getLongExtra(Notifier.EXTRA_USER, 0)
        val peerId = intent.getLongExtra(Notifier.EXTRA_PEER, 0)
        val messageId = intent.getLongExtra(Notifier.EXTRA_MESSAGE, 0)
        if (userId == 0L || peerId == 0L) return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()

        val pending = goAsync()
        container.appScope.launch {
            val (session, temporary) = container.sessionFor(userId) ?: run {
                pending.finish()
                return@launch
            }
            try {
                val repo = session.messages
                when (intent.action) {
                    ACTION_REPLY -> if (!text.isNullOrBlank()) {
                        repo.send(peerId, text)
                        if (messageId > 0) runCatching { repo.markAsRead(peerId, messageId) }
                    }
                    ACTION_READ -> if (messageId > 0) repo.markAsRead(peerId, messageId)
                }
                Notifier.cancel(context, userId, peerId)
            } catch (e: Exception) {
                Notifier.showError(context, userId, peerId, e.userMessage())
            } finally {
                if (temporary) session.close(closeDb = true)
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "app.pulse.client.action.REPLY"
        const val ACTION_READ = "app.pulse.client.action.READ"
    }
}
