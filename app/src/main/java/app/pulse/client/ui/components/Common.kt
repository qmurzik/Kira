package app.pulse.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pulse.client.ui.theme.AppTheme
import coil.compose.AsyncImage

private val avatarPalette = listOf(
    Color(0xFF7B6CF6), Color(0xFF2FB7A0), Color(0xFFF08A3C), Color(0xFFE25C7E),
    Color(0xFF4C9BE8), Color(0xFF8BBF3F),
)

@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    online: Boolean = false,
) {
    Box(modifier.size(size)) {
        if (url.isNullOrEmpty() || url.contains("camera_") || url.contains("deactivated")) {
            val color = avatarPalette[(name.hashCode() and 0x7fffffff) % avatarPalette.size]
            Box(
                Modifier.size(size).clip(CircleShape).background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    initials(name),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (size.value * 0.38f).sp,
                )
            }
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        if (online) {
            val dot = (size.value * 0.26f).coerceAtLeast(9f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(dot)
                    .clip(CircleShape)
                    .background(AppTheme.extra.online)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
    }
}

@Composable
fun MessageBox(
    title: String,
    text: String? = null,
    icon: ImageVector = Icons.Outlined.Info,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 420.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (text != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                FilledTonalButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) =
    MessageBox("Не удалось загрузить", message, Icons.Outlined.CloudOff, "Повторить", onRetry, modifier)

/**
 * Honest placeholder for features the public VK API does not provide to third-party apps.
 * Every usage is listed in docs/API-COVERAGE.md.
 */
@Composable
fun UnavailableBox(feature: String, reason: String, modifier: Modifier = Modifier) =
    MessageBox("$feature недоступно через API", reason, Icons.Outlined.Info, modifier = modifier)

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
    )
}

@Composable
fun CounterBadge(count: Int, muted: Boolean = false, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Surface(
        shape = RoundedCornerShape(50),
        color = if (muted) AppTheme.extra.mutedBadge else AppTheme.extra.unreadBadge,
        modifier = modifier,
    ) {
        Text(
            if (count > 999) "999+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun ThinDivider(startIndent: Dp = 0.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = startIndent),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
