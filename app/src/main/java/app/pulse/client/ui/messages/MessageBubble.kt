package app.pulse.client.ui.messages

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pulse.client.core.util.Format
import app.pulse.client.domain.model.Attachment
import app.pulse.client.domain.model.Message
import app.pulse.client.domain.model.Owner
import app.pulse.client.notifications.MessagesSyncWorker
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Attachments
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.components.VoiceState
import app.pulse.client.ui.theme.AppTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    m: Message,
    sender: Owner?,
    showSender: Boolean,
    read: Boolean,
    selected: Boolean,
    owners: Map<Long, Owner>,
    actions: AttachmentActions,
    voice: VoiceState,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    if (m.action != null) {
        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(
                m.action,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val stickerOnly = m.text.isEmpty() && m.attachments.size == 1 && m.attachments[0] is Attachment.Sticker
    val bg = when {
        stickerOnly -> Color.Transparent
        m.out -> AppTheme.extra.outgoingBubble
        else -> AppTheme.extra.incomingBubble
    }
    val fg = if (m.out) AppTheme.extra.onOutgoingBubble else MaterialTheme.colorScheme.onSurface
    val shape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp,
        bottomStart = if (m.out) 16.dp else 4.dp,
        bottomEnd = if (m.out) 4.dp else 16.dp,
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (m.out) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (showSender && !m.out) {
            Avatar(sender?.photo, sender?.name ?: "", 32.dp)
            Spacer(Modifier.width(6.dp))
        }
        Surface(color = bg, shape = shape, modifier = Modifier.widthIn(max = 300.dp)) {
            Column(Modifier.padding(if (stickerOnly) 0.dp else 10.dp)) {
                if (showSender && !m.out && sender != null) {
                    Text(
                        sender.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                m.reply?.let { r -> QuoteBlock(owners[r.fromId]?.name ?: "Сообщение", MessagesSyncWorker.preview(r)) }
                if (m.text.isNotEmpty()) {
                    Text(m.text, style = MaterialTheme.typography.bodyMedium, color = fg)
                }
                if (m.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Attachments(m.attachments, actions, voice = voice)
                }
                m.forwarded.forEach { f -> ForwardedBlock(f, owners, actions, voice) }
                Row(
                    Modifier.align(Alignment.End).padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (m.edited) {
                        Text("ред. ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Format.time(m.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (m.out) {
                        Spacer(Modifier.width(3.dp))
                        val (icon, tint) = when (m.state) {
                            Message.SendState.Sending -> Icons.Outlined.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
                            Message.SendState.Failed -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
                            Message.SendState.Sent -> (if (read) Icons.Filled.DoneAll else Icons.Filled.Done) to MaterialTheme.colorScheme.primary
                        }
                        Icon(icon, contentDescription = if (read) "Прочитано" else "Отправлено", tint = tint, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteBlock(author: String, text: String) {
    Row(Modifier.padding(bottom = 4.dp)) {
        Box(Modifier.width(2.dp).height(34.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(start = 6.dp)) {
            Text(author, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ForwardedBlock(f: Message, owners: Map<Long, Owner>, actions: AttachmentActions, voice: VoiceState) {
    Row(Modifier.padding(top = 4.dp)) {
        Box(Modifier.width(2.dp).height(40.dp).background(MaterialTheme.colorScheme.outline))
        Column(Modifier.padding(start = 8.dp)) {
            val o = owners[f.fromId]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(o?.photo, o?.name ?: "", 20.dp)
                Text(
                    o?.name ?: "Пересланное сообщение",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Text(" · " + Format.shortDate(f.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (f.text.isNotEmpty()) Text(f.text, style = MaterialTheme.typography.bodyMedium)
            if (f.attachments.isNotEmpty()) Attachments(f.attachments, actions, voice = voice)
            if (f.forwarded.isNotEmpty()) {
                Text("Пересланных: ${f.forwarded.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
