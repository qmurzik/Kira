package app.pulse.client.notifications

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pulse.client.PulseApp
import app.pulse.client.data.api.VkApiException
import app.pulse.client.domain.model.Attachment
import app.pulse.client.domain.model.Message
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background check for new messages (every 15+ minutes, WorkManager minimum).
 * This is not real-time push: third-party apps cannot register for VK push, see API-COVERAGE.md.
 */
class MessagesSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PulseApp).container
        val settings = container.settings.settings.first()
        if (!settings.messageNotifications || container.isForeground) return Result.success()
        if (!Notifier.canNotify(applicationContext)) return Result.success()

        val prefs = applicationContext.getSharedPreferences("notified", Context.MODE_PRIVATE)
        for (account in container.accounts.accounts.value) {
            val (session, temporary) = container.sessionFor(account.userId) ?: continue
            try {
                val unread = session.messages.loadFiltered("unread", 0)
                for (c in unread) {
                    val last = c.lastMessage ?: continue
                    if (last.out) continue
                    val key = "${account.userId}_${c.peerId}"
                    if (prefs.getLong(key, 0) >= last.id) continue
                    prefs.edit().putLong(key, last.id).apply()
                    val sender = if (c.isChat) c.lastSenderName ?: c.title else c.title
                    val line = sender to preview(last)
                    val lines = if (c.unreadCount > 1) {
                        listOf(sender to "Непрочитанных: ${c.unreadCount}", line)
                    } else {
                        listOf(line)
                    }
                    Notifier.showMessage(
                        applicationContext,
                        account.userId,
                        c.peerId,
                        last.id,
                        c.title,
                        lines,
                        settings.hideNotificationText,
                    )
                }
            } catch (e: VkApiException) {
                // Access denied or session problems: nothing to retry in background.
            } catch (e: Exception) {
                if (temporary) session.close(closeDb = true)
                return Result.retry()
            }
            if (temporary) session.close(closeDb = true)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "messages_sync"

        fun schedule(context: Context, minutes: Int, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<MessagesSyncWorker>(minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun preview(m: Message): String {
            if (m.text.isNotBlank()) return m.text
            val a = m.attachments.firstOrNull()
            return when {
                m.action != null -> m.action
                a is Attachment.Photo -> "Фотография"
                a is Attachment.Video -> "Видеозапись"
                a is Attachment.Voice -> "Голосовое сообщение"
                a is Attachment.Sticker -> "Стикер"
                a is Attachment.Doc -> "Документ"
                a is Attachment.Link -> "Ссылка"
                a is Attachment.Audio -> "Аудиозапись"
                a is Attachment.Poll -> "Опрос"
                a is Attachment.WallPost -> "Запись на стене"
                m.forwarded.isNotEmpty() -> "Пересланные сообщения"
                a != null -> "Вложение"
                else -> ""
            }
        }
    }
}
