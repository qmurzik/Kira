package app.pulse.client.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pulse.client.core.util.Format
import app.pulse.client.domain.model.Owner
import app.pulse.client.domain.model.Post
import app.pulse.client.ui.components.AttachmentActions
import app.pulse.client.ui.components.Attachments
import app.pulse.client.ui.components.Avatar
import app.pulse.client.ui.theme.AppTheme

@Composable
fun PostCard(
    post: Post,
    actions: AttachmentActions,
    onAuthor: (Long) -> Unit,
    onOpen: (() -> Unit)?,
    onLike: () -> Unit,
    onRepost: () -> Unit,
    modifier: Modifier = Modifier,
    expandedByDefault: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            PostHeader(post.author, post.date, post.isAd, post.pinned, onAuthor, Modifier.padding(horizontal = 12.dp))
            PostBody(post, actions, onAuthor, onOpen, expandedByDefault)
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Counter(
                    if (post.liked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    post.likes,
                    if (post.liked) AppTheme.extra.like else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = post.canLike,
                    label = if (post.liked) "Убрать отметку" else "Нравится",
                    onClick = onLike,
                )
                Counter(
                    Icons.Outlined.ChatBubbleOutline,
                    post.comments,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = onOpen != null,
                    label = "Комментарии",
                    onClick = { onOpen?.invoke() },
                )
                Counter(
                    Icons.Outlined.Repeat,
                    post.reposts,
                    if (post.reposted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = !post.reposted,
                    label = "Поделиться",
                    onClick = onRepost,
                )
                Spacer(Modifier.weight(1f))
                if (post.views > 0) {
                    Icon(Icons.Outlined.Visibility, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        Format.count(post.views),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostBody(post: Post, actions: AttachmentActions, onAuthor: (Long) -> Unit, onOpen: (() -> Unit)?, expandedByDefault: Boolean) {
    var expanded by rememberSaveable(post.key) { mutableStateOf(expandedByDefault) }
    val clickable = Modifier.let { if (onOpen != null) it.clickable(onClick = onOpen) else it }
    if (post.text.isNotBlank()) {
        Column(clickable.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                post.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis,
            )
            if (!expanded && (post.text.length > 400 || post.text.count { it == '\n' } > 7)) {
                Text(
                    "Показать полностью…",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp).clickable { expanded = true },
                )
            }
        }
    } else {
        Spacer(Modifier.height(8.dp))
    }
    if (post.attachments.isNotEmpty()) {
        Attachments(post.attachments, actions, Modifier.padding(horizontal = 12.dp))
    }
    post.copyHistory.firstOrNull()?.let { orig ->
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            Box(Modifier.width(2.dp).height(36.dp).background(MaterialTheme.colorScheme.outline))
            Column(Modifier.padding(start = 10.dp).clickable { actions.onWall(orig.ownerId, orig.id) }) {
                PostHeader(orig.author, orig.date, false, false, onAuthor, Modifier, small = true)
                if (orig.text.isNotBlank()) {
                    Text(
                        orig.text,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded) Int.MAX_VALUE else 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                Attachments(orig.attachments, actions)
            }
        }
    }
}

@Composable
fun PostHeader(
    author: Owner,
    date: Long,
    isAd: Boolean,
    pinned: Boolean,
    onAuthor: (Long) -> Unit,
    modifier: Modifier,
    small: Boolean = false,
) {
    Row(modifier.clickable { onAuthor(author.id) }, verticalAlignment = Alignment.CenterVertically) {
        Avatar(author.photo, author.name, if (small) 32.dp else 44.dp)
        Column(Modifier.padding(start = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    author.name,
                    style = if (small) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (author.verified) {
                    Icon(
                        Icons.Filled.Verified,
                        contentDescription = "Подтверждён",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                    )
                }
            }
            val meta = buildString {
                append(Format.postDate(date))
                if (pinned) append(" · запись закреплена")
                if (isAd) append(" · реклама")
            }
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Counter(icon: ImageVector, count: Int, tint: Color, enabled: Boolean, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        if (count > 0) {
            Text(
                Format.count(count),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                modifier = Modifier.padding(start = 5.dp),
            )
        }
    }
}
