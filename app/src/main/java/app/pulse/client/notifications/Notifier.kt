package app.pulse.client.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import app.pulse.client.MainActivity
import app.pulse.client.R

object Notifier {
    const val CH_MESSAGES = "messages"
    const val CH_MEDIA = "media"
    const val CH_EVENTS = "events"

    const val KEY_REPLY = "reply_text"
    const val EXTRA_USER = "user_id"
    const val EXTRA_PEER = "peer_id"
    const val EXTRA_MESSAGE = "message_id"
    const val EXTRA_OPEN_PEER = "open_peer"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_MESSAGES, context.getString(R.string.channel_messages), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_messages_desc) },
                NotificationChannel(CH_MEDIA, context.getString(R.string.channel_media), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_media_desc) },
                NotificationChannel(CH_EVENTS, context.getString(R.string.channel_events), NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = context.getString(R.string.channel_events_desc) },
            )
        )
    }

    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun notificationId(userId: Long, peerId: Long) = (userId * 31 + peerId).hashCode()

    /** Message notification with inline reply and "mark as read" actions. */
    fun showMessage(
        context: Context,
        userId: Long,
        peerId: Long,
        lastMessageId: Long,
        title: String,
        lines: List<Pair<String, String>>,
        hideText: Boolean,
    ) {
        if (!canNotify(context)) return
        val id = notificationId(userId, peerId)

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_OPEN_PEER, peerId)
            .putExtra(EXTRA_USER, userId)
        val contentIntent = PendingIntentCompat.getActivity(context, id, open, 0, false)

        val builder = NotificationCompat.Builder(context, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

        if (hideText) {
            builder.setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.notification_hidden_text))
        } else {
            val me = Person.Builder().setName("Вы").build()
            val style = NotificationCompat.MessagingStyle(me).setConversationTitle(title)
            style.isGroupConversation = lines.map { it.first }.distinct().size > 1
            val now = System.currentTimeMillis()
            lines.takeLast(6).forEachIndexed { i, (sender, text) ->
                style.addMessage(text, now - (lines.size - i) * 1000L, Person.Builder().setName(sender).build())
            }
            builder.setStyle(style)
                .setContentTitle(title)
                .setContentText(lines.lastOrNull()?.second.orEmpty())

            val replyIntent = Intent(context, ReplyReceiver::class.java)
                .setAction(ReplyReceiver.ACTION_REPLY)
                .putExtra(EXTRA_USER, userId)
                .putExtra(EXTRA_PEER, peerId)
                .putExtra(EXTRA_MESSAGE, lastMessageId)
            // RemoteInput requires a mutable PendingIntent.
            val replyPending = PendingIntentCompat.getBroadcast(context, id, replyIntent, 0, true)
            val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.reply_hint)).build()
            val replyAction = NotificationCompat.Action.Builder(0, context.getString(R.string.action_reply), replyPending)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .build()
            builder.addAction(replyAction)
        }

        val readIntent = Intent(context, ReplyReceiver::class.java)
            .setAction(ReplyReceiver.ACTION_READ)
            .putExtra(EXTRA_USER, userId)
            .putExtra(EXTRA_PEER, peerId)
            .putExtra(EXTRA_MESSAGE, lastMessageId)
        val readPending = PendingIntentCompat.getBroadcast(context, id + 1, readIntent, 0, false)
        builder.addAction(0, context.getString(R.string.action_mark_read), readPending)

        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, userId: Long, peerId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(userId, peerId))
    }

    fun showError(context: Context, userId: Long, peerId: Long, text: String) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CH_MESSAGES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_sending_failed))
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId(userId, peerId), n)
        } catch (e: SecurityException) {
        }
    }
}
